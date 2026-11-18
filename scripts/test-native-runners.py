"""Real-process, real-gRPC contract tests. No database or external package registry required.
Usage: SKY_TEST_NODE=/path/to/node python scripts/test-native-runners.py
Run with the Python build venv after generating both language contracts.
"""
from pathlib import Path
import concurrent.futures
import contextlib
import json
import os
import socket
import subprocess
import sys
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "sky-plugin-runner-python/generated"))
import grpc
import sky_plugin_pb2 as pb
import sky_plugin_pb2_grpc as services

NODE = os.environ.get("SKY_TEST_NODE", "node")
META = [("authorization", "Bearer test-token"), ("x-sky-plugin-id", "test"), ("x-sky-plugin-instance-id", "instance")]


class Host(services.PluginHostServiceServicer):
    def __init__(self): self.calls = []
    def check(self, context):
        if dict(context.invocation_metadata()).get("authorization") != "Bearer test-token": context.abort(grpc.StatusCode.UNAUTHENTICATED, "token")
    def GetConfig(self, request, context):
        self.check(context)
        return pb.ConfigResponse(success=True, config_json='{"message":"中文"}')
    def UpdateConfig(self, request, context):
        self.check(context); self.calls.append(("config", json.loads(request.config_json)))
        return pb.ConfigResponse(success=True, config_json=request.config_json)
    def ListAccounts(self, request, context):
        self.check(context)
        return pb.AccountResponse(success=True, accounts=[pb.AccountRecord(account_id="a", account_json='{"value":1}', enabled=True), pb.AccountRecord(account_id="b", enabled=False)])
    def GetAccount(self, request, context):
        self.check(context)
        return pb.AccountResponse(success=True, accounts=[pb.AccountRecord(account_id=request.account_id, account_json='{}', enabled=True)])
    def UpdateAccount(self, request, context):
        self.check(context); self.calls.append(("account", request.account_id))
        return pb.AccountResponse(success=True)
    def GetData(self, request, context):
        self.check(context)
        return pb.PluginDataResponse(success=True, data_json='{"value":2}')
    def PutData(self, request, context):
        self.check(context); self.calls.append(("data", json.loads(request.data_json)))
        return pb.PluginDataResponse(success=True)
    def DeleteData(self, request, context): return pb.PluginDataResponse(success=True, deleted=True)
    def ListData(self, request, context): return pb.PluginDataResponse(success=True, records=[pb.PluginDataRecord(key="k", data_json='{}')])
    def ListNotificationChannels(self, request, context): return pb.NotificationChannelListResponse(channels=[pb.NotificationChannelInfo(channel_id="c", enabled=True)])
    def SendNotification(self, request, context):
        self.check(context); self.calls.append(("notify", request.request_id))
        return pb.NotificationResponse(success=True, receipts=[pb.NotificationSendReceipt(history_id=9007199254740993)])
    def CallPluginService(self, request, context):
        self.check(context)
        if request.caller_plugin_id != "test" or request.target_installation_id != "target": context.abort(grpc.StatusCode.PERMISSION_DENIED, "caller/target mismatch")
        self.calls.append(("service", request.target_installation_id))
        return pb.PluginServiceCallResponse(success=True, result_json='{"ok":true}')


JS = '''export async function run(c) {
 const config=await c.config.get(); console.log(config.message);
 await c.config.update({changed:true});
 const accounts=await c.accounts.list();
 if(accounts.length!==1) throw Error('disabled account leaked');
 await c.accounts.get('a'); await c.accounts.update('a',{value:2});
 await c.data.get('x','k'); await c.data.put('x','k',{value:3});
 await c.data.list('x'); await c.data.delete('x','k');
 await c.notifications.listChannels();
 const receipt=await c.notifications.send({title:'test',content:'中文'});
 if(receipt.receipts[0].historyId!=='9007199254740993') throw Error('ID precision lost');
 await c.services.callInstallation('target','test',{});
 return c.success('完成',{input:c.input,installation:c.installationId});
}'''
PY = '''def run(c):
    config=c.config.get(); print(config['message'])
    c.config.update({'changed':True})
    assert len(c.accounts.list())==1
    c.accounts.get('a'); c.accounts.update('a',{'value':2})
    c.data.get('x','k'); c.data.put('x','k',{'value':3})
    c.data.list('x'); c.data.delete('x','k')
    c.notifications.list_channels()
    receipt=c.notifications.send({'title':'test','content':'中文'})
    assert receipt['receipts'][0]['historyId']=='9007199254740993'
    c.services.call_installation('target','test',{})
    return c.success('完成',{'input':c.input,'installation':c.installation_id})
'''


class RunnerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.host = Host()
        cls.server = grpc.server(concurrent.futures.ThreadPoolExecutor(max_workers=4))
        services.add_PluginHostServiceServicer_to_server(cls.host, cls.server)
        cls.host_port = cls.server.add_insecure_port('127.0.0.1:0')
        cls.server.start()
    @classmethod
    def tearDownClass(cls): cls.server.stop(0).wait()

    @contextlib.contextmanager
    def runner(self, engine, code, mode='plugin', extension=None):
        with tempfile.TemporaryDirectory(prefix='sky-native-') as folder:
            work=Path(folder); entry=work/('entry'+(extension or ('.mjs' if engine=='node' else '.py'))); entry.write_text(code,encoding='utf-8')
            (work/'package.json').write_text('{"type":"commonjs"}')
            with socket.socket() as sock: sock.bind(('127.0.0.1',0)); port=sock.getsockname()[1]
            env=os.environ.copy(); env.update(SKY_PLUGIN_ID='test', SKY_PLUGIN_INSTALLATION_ID='installation', SKY_PLUGIN_INSTANCE_ID='instance',
                SKY_PLUGIN_TOKEN='test-token', SKY_PLUGIN_PORT=str(port), SKY_HOST_GRPC_PORT=str(self.host_port), PYTHONUTF8='1', PYTHONUNBUFFERED='1', SKY_PLUGIN_EXECUTION_MODE=mode, HOST_SECRET_TEST='must-not-inherit')
            cmd=[NODE,str(ROOT/'sky-plugin-runner-node/runner.mjs'),str(entry)] if engine=='node' else [sys.executable,str(ROOT/'sky-plugin-runner-python/runner.py'),str(entry)]
            with (work/'runner.log').open('wb') as log:
                process=subprocess.Popen(cmd,cwd=work,env=env,stdout=log,stderr=subprocess.STDOUT)
                channel=grpc.insecure_channel(f'127.0.0.1:{port}')
                try:
                    grpc.channel_ready_future(channel).result(timeout=12)
                    stub=services.PluginRunnerServiceStub(channel)
                    self.assertTrue(stub.Health(pb.HealthRequest(plugin_id='test',instance_id='instance'),metadata=META,timeout=3).healthy)
                    yield stub,work,process
                except grpc.FutureTimeoutError:
                    self.fail((work/'runner.log').read_text(encoding='utf-8',errors='replace'))
                finally:
                    channel.close(); process.terminate()
                    try: process.wait(timeout=5)
                    except subprocess.TimeoutExpired: process.kill();process.wait(timeout=5)

    def run_task(self, stub, timeout=5000):
        return stub.RunTask(pb.TaskRunRequest(plugin_id='test',request_id='req',job_id='9007199254740993',input_json='{"n":1}',timeout_millis=timeout),metadata=META,timeout=max(2,timeout/1000+1))

    def test_platform_sdk_and_json_ids(self):
        for engine,code in [('node',JS),('python',PY)]:
            with self.subTest(engine=engine), self.runner(engine,code) as (stub,work,process):
                result=self.run_task(stub)
                self.assertEqual(result.status,pb.TASK_RUN_STATUS_SUCCESS,result.error_message)
                self.assertEqual(json.loads(result.result_json),{'input':{'n':1},'installation':'installation'})
                self.assertIn('中文',(work/'runner.log').read_text(encoding='utf-8'))

    def test_auth_and_unsupported_services(self):
        for engine in ['node','python']:
            with self.subTest(engine=engine), self.runner(engine,'raise Exception("must not import at health")' if engine=='python' else 'throw Error("must not import at health")') as (stub,_,__):
                with self.assertRaises(grpc.RpcError) as error: stub.Health(pb.HealthRequest(),timeout=2)
                self.assertEqual(error.exception.code(),grpc.StatusCode.UNAUTHENTICATED)
                with self.assertRaises(grpc.RpcError) as error: stub.Health(pb.HealthRequest(plugin_id='other'),metadata=META,timeout=2)
                self.assertEqual(error.exception.code(),grpc.StatusCode.PERMISSION_DENIED)
                with self.assertRaises(grpc.RpcError) as error: stub.CallService(pb.PluginServiceRunRequest(),metadata=META,timeout=2)
                self.assertEqual(error.exception.code(),grpc.StatusCode.UNIMPLEMENTED)

    def test_failures_and_invalid_results(self):
        for engine,cases in [('node',['export function run(c) { return c.failure("business"); }','export function run() { return {}; }','export function run() { throw Error("broken"); }']),
                             ('python',['def run(c): return c.failure("business")','def run(c): return {}','def run(c): raise ValueError("broken")','def run(c): raise SystemExit(1)'])]:
            for code in cases:
                with self.subTest(engine=engine,code=code), self.runner(engine,code) as (stub,_,__):
                    self.assertEqual(self.run_task(stub).status,pb.TASK_RUN_STATUS_FAILED)

    def test_import_hang_deadline(self):
        for engine,code in [('node','while(true) {}'),('python','while True: pass')]:
            with self.subTest(engine=engine), self.runner(engine,code) as (stub,_,__):
                start=time.monotonic()
                try:
                    result=stub.RunTask(pb.TaskRunRequest(plugin_id='test',request_id='req',timeout_millis=300),metadata=META,timeout=.5)
                    self.assertEqual(result.status,pb.TASK_RUN_STATUS_TIMEOUT)
                except grpc.RpcError as error: self.assertEqual(error.code(),grpc.StatusCode.DEADLINE_EXCEEDED)
                self.assertLess(time.monotonic()-start,2)

    def test_cancellation(self):
        for engine,code in [('node','export async function run(c){ await new Promise(r=>setTimeout(r,1000)); return c.success(); }'),
                             ('python','import time\ndef run(c):\n time.sleep(1)\n return c.success()')]:
            with self.subTest(engine=engine),self.runner(engine,code) as (stub,_,__):
                future=stub.RunTask.future(pb.TaskRunRequest(plugin_id='test',request_id='req',timeout_millis=5000),metadata=META,timeout=6)
                time.sleep(.15)
                self.assertTrue(stub.CancelTask(pb.CancelTaskRequest(request_id='req',plugin_id='test'),metadata=META,timeout=2).cancelled)
                self.assertEqual(future.result().status,pb.TASK_RUN_STATUS_CANCELLED)

    def test_standalone_environment_arguments_main_and_exit(self):
        cases=[('node','.js',"const assert=require('node:assert');assert.equal(process.env.dxqy,'中文@#\\nsecond');assert.equal(process.argv[2],'a b & $()');assert.equal(process.env.SKY_PLUGIN_TOKEN,undefined);assert.equal(process.env.HOST_SECRET_TEST,undefined);console.log('中文 standalone');process.exit(0)"),
               ('node','.mjs',"import assert from 'node:assert';assert.equal(process.env.dxqy,'中文@#\\nsecond');console.log('中文 standalone');process.exit(0)"),
               ('python','.py',"import os,sys,argparse\nif __name__=='__main__':\n assert os.getenv('dxqy')=='中文@#\\nsecond'\n assert sys.argv[1]=='a b & $()'\n assert 'SKY_PLUGIN_TOKEN' not in os.environ and 'HOST_SECRET_TEST' not in os.environ\n print('中文 standalone')\n sys.exit(0)")]
        for engine,extension,code in cases:
            with self.subTest(extension=extension),self.runner(engine,code,'standalone',extension) as (stub,work,_):
                request=pb.TaskRunRequest(plugin_id='test',request_id='req',timeout_millis=5000,standalone_options=pb.StandaloneTaskOptions(arguments=['a b & $()'],environment={'dxqy':'中文@#\nsecond'}))
                response=stub.RunTask(request,metadata=META,timeout=6)
                self.assertEqual(response.status,pb.TASK_RUN_STATUS_SUCCESS,response)
                self.assertEqual(json.loads(response.result_json)['exitCode'],0)
                self.assertIn('中文 standalone',(work/'runner.log').read_text(encoding='utf-8'))

    def test_standalone_failure_timeout_cancel_and_mode_validation(self):
        for engine in ['node','python']:
            for action in ['failure','timeout','cancel','mode']:
                code=('process.exit(3)' if engine=='node' else 'import sys;sys.exit(3)') if action=='failure' else ('while(true){}' if engine=='node' else 'while True: pass')
                with self.subTest(engine=engine,action=action),self.runner(engine,code,'standalone') as (stub,_,__):
                    if action=='mode':
                        with self.assertRaises(grpc.RpcError) as error:self.run_task(stub)
                        self.assertEqual(error.exception.code(),grpc.StatusCode.INVALID_ARGUMENT)
                        continue
                    request=pb.TaskRunRequest(plugin_id='test',request_id='req',timeout_millis=300 if action=='timeout' else 5000,standalone_options=pb.StandaloneTaskOptions())
                    future=stub.RunTask.future(request,metadata=META,timeout=7)
                    if action=='cancel':
                        time.sleep(.15)
                        self.assertTrue(stub.CancelTask(pb.CancelTaskRequest(plugin_id='test',request_id='req'),metadata=META,timeout=2).cancelled)
                    response=future.result()
                    self.assertEqual(response.status,{'failure':pb.TASK_RUN_STATUS_FAILED,'timeout':pb.TASK_RUN_STATUS_TIMEOUT,'cancel':pb.TASK_RUN_STATUS_CANCELLED}[action])
                    if action=='failure':self.assertEqual(json.loads(response.result_json)['exitCode'],3)


if __name__=='__main__': unittest.main(verbosity=2)
