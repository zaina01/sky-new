import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent / "generated"))
import concurrent.futures
import importlib.util
import logging
import os
import secrets
import threading
import time
import traceback
import grpc
import sky_plugin_pb2 as pb
import sky_plugin_pb2_grpc as services
from sdk import Context, TaskResult, encode
from standalone import run_script

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")


class Runner(services.PluginRunnerServiceServicer):
    def __init__(self, env, entry):
        self.env, self.entry = env, entry
        self.lock = threading.Lock()
        self.used = False
        self.active = None
        limit = int(env.get("SKY_PLUGIN_GRPC_MAX_INBOUND_MESSAGE_BYTES", "1048576"))
        self.channel = grpc.insecure_channel(f'{env.get("SKY_HOST_GRPC_HOST", "127.0.0.1")}:{env["SKY_HOST_GRPC_PORT"]}',
            options=[("grpc.max_receive_message_length", limit), ("grpc.max_send_message_length", limit), ("grpc.enable_retries", 0)])
        self.host = services.PluginHostServiceStub(self.channel)

    def auth(self, request, context):
        metadata = dict(context.invocation_metadata())
        if not secrets.compare_digest(metadata.get("authorization", ""), "Bearer " + self.env["SKY_PLUGIN_TOKEN"]):
            context.abort(grpc.StatusCode.UNAUTHENTICATED, "Invalid runtime credentials")
        if metadata.get("x-sky-plugin-id") != self.env["SKY_PLUGIN_ID"] or metadata.get("x-sky-plugin-instance-id") != self.env["SKY_PLUGIN_INSTANCE_ID"]:
            context.abort(grpc.StatusCode.PERMISSION_DENIED, "Plugin identity mismatch")
        for field, key in [("plugin_id", "SKY_PLUGIN_ID"), ("instance_id", "SKY_PLUGIN_INSTANCE_ID")]:
            value = getattr(request, field, "")
            if value and value != self.env[key]: context.abort(grpc.StatusCode.PERMISSION_DENIED, "Request identity mismatch")

    def Health(self, request, context):
        self.auth(request, context)
        return pb.HealthResponse(healthy=True, message="Python Runner ready")

    def StartPlugin(self, request, context):
        self.auth(request, context)
        return pb.StartPluginResponse(started=True, message="Task Runner ready")

    def CallService(self, request, context):
        self.auth(request, context)
        context.abort(grpc.StatusCode.UNIMPLEMENTED, "Script service plugins are not supported")

    def RunTask(self, request, context):
        self.auth(request, context)
        standalone = self.env.get('SKY_PLUGIN_EXECUTION_MODE') == 'standalone'
        if standalone != request.HasField('standalone_options'):
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, 'Task options do not match Runner execution mode')
        with self.lock:
            if self.used: context.abort(grpc.StatusCode.FAILED_PRECONDITION, "This Runner already accepted a task")
            self.used = True
            cancelled = threading.Event()
            self.active = (request.request_id, cancelled)
        start = int(time.time()*1000)
        maximum = int(self.env.get("SKY_PLUGIN_MAX_TASK_TIMEOUT_MILLIS", "120000"))
        timeout = min(request.timeout_millis if request.timeout_millis > 0 else maximum, maximum)/1000
        timeout = min(timeout, context.time_remaining()) if context.time_remaining() is not None else timeout
        deadline = time.monotonic() + timeout
        context.add_callback(cancelled.set)
        response = pb.TaskRunResponse(request_id=request.request_id, started_at_epoch_millis=start)
        try:
            if standalone:
                result = run_script(self.entry, request.standalone_options, cancelled, deadline)
                response.result_json = encode(result)
                response.status = pb.TASK_RUN_STATUS_SUCCESS if result['exitCode'] == 0 else pb.TASK_RUN_STATUS_FAILED
                response.message = f"Script exited with code {result['exitCode']}"
                if result['exitCode'] != 0: response.error_type = 'SCRIPT_EXIT'
                return response
            sys.path.insert(0, str(self.entry.parent))
            spec = importlib.util.spec_from_file_location("sky_user_plugin", self.entry)
            module = importlib.util.module_from_spec(spec)
            sys.modules[spec.name] = module
            spec.loader.exec_module(module)
            if not callable(getattr(module, "run", None)): raise TypeError("Entry must define run(ctx)")
            result = module.run(Context(request, self.env, self.host, deadline, cancelled))
            if not isinstance(result, TaskResult): raise TypeError("run(ctx) must return ctx.success() or ctx.failure()")
            response.result_json = encode(result.data)
            response.message = result.message
            response.status = pb.TASK_RUN_STATUS_SUCCESS if result.succeeded else pb.TASK_RUN_STATUS_FAILED
        except BaseException as error:
            traceback.print_exc()
            response.status = pb.TASK_RUN_STATUS_FAILED
            response.message = "Plugin task failed"
            response.error_type = type(error).__name__
            response.error_message = str(error)
        finally:
            if time.monotonic() >= deadline: response.status = pb.TASK_RUN_STATUS_TIMEOUT
            elif cancelled.is_set(): response.status = pb.TASK_RUN_STATUS_CANCELLED
            response.finished_at_epoch_millis = int(time.time()*1000)
            with self.lock: self.active = None
        return response

    def CancelTask(self, request, context):
        self.auth(request, context)
        with self.lock:
            cancelled = self.active is not None and self.active[0] == request.request_id
            if cancelled: self.active[1].set()
        return pb.CancelTaskResponse(cancelled=cancelled, message="Cancellation requested" if cancelled else "Task not running")

    def StopPlugin(self, request, context):
        self.auth(request, context)
        with self.lock:
            if self.active: self.active[1].set()
        return pb.StopPluginResponse(stopped=True, message="Host may terminate this process")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--check":
        print("Python Runner protocol and SDK ready")
        raise SystemExit(0)
    for key in ["SKY_PLUGIN_ID", "SKY_PLUGIN_INSTALLATION_ID", "SKY_PLUGIN_INSTANCE_ID", "SKY_PLUGIN_TOKEN", "SKY_PLUGIN_PORT", "SKY_HOST_GRPC_PORT"]:
        if not os.environ.get(key): raise RuntimeError(f"Missing {key}")
    runner = Runner(os.environ, Path(sys.argv[1]).resolve())
    limit = int(os.environ.get("SKY_PLUGIN_GRPC_MAX_INBOUND_MESSAGE_BYTES", "1048576"))
    server = grpc.server(concurrent.futures.ThreadPoolExecutor(max_workers=4), options=[("grpc.max_receive_message_length", limit), ("grpc.max_send_message_length", limit)])
    services.add_PluginRunnerServiceServicer_to_server(runner, server)
    port = server.add_insecure_port(f'{os.environ.get("SKY_PLUGIN_GRPC_BIND_HOST", "127.0.0.1")}:{os.environ["SKY_PLUGIN_PORT"]}')
    if not port: raise RuntimeError("Failed to bind Python Runner")
    server.start()
    server.wait_for_termination()
