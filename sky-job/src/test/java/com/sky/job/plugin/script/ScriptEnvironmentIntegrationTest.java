package com.sky.job.plugin.script;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sky.job.plugin.script.mapper.ScriptEnvironmentMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit opt-in: downloads test packages into isolated temporary environments. */
@Tag("native-integration")
class ScriptEnvironmentIntegrationTest {
    @TempDir Path temporary;
    ScriptEnvironmentService service;
    ScriptRuntimeProperties properties;
    @BeforeEach void setup() {
        Path root=Path.of(System.getProperty("sky.script.workspace")).toAbsolutePath();
        var mapper=mock(ScriptEnvironmentMapper.class);Map<String,String> data=new ConcurrentHashMap<>();
        when(mapper.get(anyString())).thenAnswer(i->data.get(i.getArgument(0)));
        when(mapper.list(anyString())).thenAnswer(i->data.entrySet().stream().filter(e->e.getKey().startsWith(i.getArgument(0))).map(Map.Entry::getValue).toList());
        doAnswer(i->{data.put(i.getArgument(0),i.getArgument(1));return null;}).when(mapper).put(anyString(),anyString());
        properties=new ScriptRuntimeProperties(temporary,System.getenv().getOrDefault("SKY_TEST_NODE","node"),System.getenv().getOrDefault("SKY_TEST_PYTHON","python"),
                System.getenv("SKY_TEST_NPM_CLI")==null?null:Path.of(System.getenv("SKY_TEST_NPM_CLI")),root.resolve("sky-plugin-runner-node/runner.mjs"),root.resolve("sky-plugin-runner-python/runner.py"),2,1048576,Duration.ofMinutes(2));
        service=new ScriptEnvironmentService(properties,new ScriptEnvironmentStore(mapper,new ObjectMapper()),new ObjectMapper());
    }
    @AfterEach void close(){service.close();}
    private ObjectNode change(String engine,String action,String name,String version)throws Exception {
        var op=service.submit(engine,action,name,version,"integration");String id=op.path("id").asText();
        long deadline=System.nanoTime()+Duration.ofMinutes(3).toNanos();
        while(System.nanoTime()<deadline){op=service.operation(id);if(Set.of("FAILED","SUCCEEDED").contains(op.path("status").asText())) return op;Thread.sleep(100);}
        throw new AssertionError("Operation timed out: "+service.log(id));
    }
    private void success(ObjectNode op)throws Exception {assertThat(op.path("status").asText()).withFailMessage("%s\n%s",op,service.log(op.path("id").asText())).isEqualTo("SUCCEEDED");}
    @Test void npmSnapshotsEsmResolutionRollbackAndTransitiveUninstall()throws Exception {
        success(change("node","install","is-number","6.0.0"));
        var old=service.acquire("node");
        success(change("node","install","is-odd","3.0.1"));
        assertThat(service.environment("node").path("directDependencies").path("is-number").asText()).isEqualTo("6.0.0");
        success(change("node","upgrade","is-number","7.0.0"));
        var current=service.acquire("node");
        assertThat(current.revision()).isNotEqualTo(old.revision());
        assertThat(Files.readString(old.path().resolve("node_modules/is-number/package.json"))).contains("6.0.0");
        Path module=current.path().resolve("runs/test/plugin/main.mjs");Files.createDirectories(module.getParent());
        Files.writeString(module,"import number from 'is-number'; import odd from 'is-odd'; if(!number(3)||!odd(3))process.exit(1); console.log('esm-ok');");
        assertThat(ScriptProcesses.run(List.of(properties.nodeCommand(),module.toString()),temporary,temporary.resolve("esm.log"),Duration.ofSeconds(10),8192)).contains("esm-ok");
        var failed=change("node","install","is-number","99999.0.0");assertThat(failed.path("status").asText()).isEqualTo("FAILED");
        assertThat(service.environment("node").path("revision").asText()).isEqualTo(current.revision());
        success(change("node","uninstall","is-number",""));
        assertThat(service.environment("node").path("directDependencies").has("is-number")).isFalse();
        assertThat(service.environment("node").path("packages").toString()).contains("is-number");
        service.release(old);service.release(current);
    }
    @Test void pipSharedEnvironmentUpgradeAndUninstall()throws Exception {
        success(change("python","install","six","1.16.0"));
        var old=service.acquire("python");
        success(change("python","upgrade","six","1.17.0"));
        var current=service.acquire("python");
        assertThat(ScriptProcesses.run(List.of(service.python(old.path()).toString(),"-c","import six;print(six.__version__)"),temporary,temporary.resolve("old.log"),Duration.ofSeconds(10),8192)).contains("1.16.0");
        assertThat(ScriptProcesses.run(List.of(service.python(current.path()).toString(),"-c","import six;print(six.__version__)"),temporary,temporary.resolve("new.log"),Duration.ofSeconds(10),8192)).contains("1.17.0");
        success(change("python","uninstall","six",""));
        assertThat(service.environment("python").path("packages").has("six")).isFalse();
        assertThat(service.environment("python").path("packages").path("grpcio").asText()).isEqualTo("1.76.0");
        service.release(old);service.release(current);
    }
    @Test void nativeHostLaunchesRunsAndKillsChildrenForBothLanguages()throws Exception {
        var processProperties=new com.sky.job.plugin.runtime.process.ProcessPluginRuntimeProperties(null,null,null,null,null,null,null,0,"java","256m",Path.of("missing-java-runner.jar"),1048576,temporary,temporary,temporary.resolve("logs"));
        var settings=mock(com.sky.job.system.DynamicProcessPluginRuntimeSettings.class);
        when(settings.startupTimeout()).thenReturn(Duration.ofSeconds(10));when(settings.taskTimeout()).thenReturn(Duration.ofSeconds(10));when(settings.stopTimeout()).thenReturn(Duration.ofSeconds(1));
        var health=new com.sky.job.plugin.runtime.process.ProcessPluginHealthChecker(processProperties);
        var tokens=new com.sky.job.plugin.security.PluginRuntimeTokenRegistry();
        var validator=new com.sky.job.plugin.runtime.process.ProcessPluginRuntimeValidator(processProperties,new com.sky.job.plugin.artifact.dependency.PluginDependencyResolutionProperties(temporary,null,0,0,false));
        var launcher=new ScriptRuntimeLauncher(properties,service,new ScriptArchive(new ObjectMapper()),new com.sky.job.plugin.runtime.process.ProcessPluginPortAllocator(),validator,health,processProperties,settings,tokens);
        var invoker=new com.sky.job.execution.PluginTaskInvoker(1048576);
        try {
            for(String engine:List.of("node","python")) {
                success(change(engine,"initialize","",""));
                String code=engine.equals("node") ? "export function run(c){console.log('中文');return c.success('done',{value:42})}" : "def run(c):\n print('中文')\n return c.success('done',{'value':42})";
                var spec=spec(engine,code);var handle=launcher.start(spec);
                try {
                    var response=invoker.invoke(handle,com.sky.plugin.protocol.TaskRunRequest.newBuilder().setPluginId("test").setRequestId("req").setTimeoutMillis(5000).build(),Duration.ofSeconds(5));
                    assertThat(response.getStatus()).isEqualTo(com.sky.plugin.protocol.TaskRunStatus.TASK_RUN_STATUS_SUCCESS);
                    assertThat(response.getResultJson()).contains("42");assertThat(handle.environmentRevision()).isNotBlank();
                } finally {((ScriptRuntimeLauncher.Handle)handle).stop(Duration.ofSeconds(1));}
                var snapshot=service.acquire(engine);
                try {assertThat(snapshot.path().resolve("runs").resolve(spec.runId())).doesNotExist();}
                finally {service.release(snapshot);}
                assertThat(Files.readString(handle.logPath())).contains("中文");assertThat(tokens.authenticate(handle.pluginId(),handle.instanceId(),handle.token())).isEmpty();
                String childCode=engine.equals("node") ? "import {spawn} from 'node:child_process';export async function run(c){const child=spawn(process.execPath,['-e','setInterval(()=>{},1000)']);console.log('CHILD:'+child.pid);await new Promise(r=>setTimeout(r,60000));return c.success()}" : "import subprocess,sys,time\ndef run(c):\n child=subprocess.Popen([sys.executable,'-c','import time;time.sleep(60)'])\n print('CHILD:'+str(child.pid),flush=True)\n time.sleep(60)\n return c.success()";
                var childHandle=launcher.start(spec(engine,childCode));
                var future=java.util.concurrent.CompletableFuture.runAsync(()->invoker.invoke(childHandle,com.sky.plugin.protocol.TaskRunRequest.newBuilder().setPluginId("test").setRequestId("child").setTimeoutMillis(9000).build(),Duration.ofSeconds(9)));
                long pid=0;
                try {
                    for(int i=0;i<100;i++) {
                        String log=Files.readString(childHandle.logPath());var matcher=java.util.regex.Pattern.compile("CHILD:(\\d+)").matcher(log);
                        if(matcher.find()){pid=Long.parseLong(matcher.group(1));break;}Thread.sleep(50);
                    }
                    assertThat(pid).isPositive();Thread.sleep(200);
                } finally {((ScriptRuntimeLauncher.Handle)childHandle).stop(Duration.ofSeconds(1));}
                assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
                try{future.get(5,java.util.concurrent.TimeUnit.SECONDS);}catch(java.util.concurrent.ExecutionException expected){/* Process termination closes the RPC. */}
            }
            var node=launcher.start(spec("node","export function run(c){return c.success()}"));
            var python=launcher.start(spec("python","def run(c): return c.success()"));
            launcher.shutdown();
            assertThat(((ScriptRuntimeLauncher.Handle)node).alive()).isFalse();
            assertThat(((ScriptRuntimeLauncher.Handle)python).alive()).isFalse();
            assertThat(tokens.authenticate(node.pluginId(),node.instanceId(),node.token())).isEmpty();
            var afterShutdown=spec("node","export function run(c){return c.success()}");
            assertThatThrownBy(()->launcher.start(afterShutdown)).hasMessageContaining("shutting down");
        } finally {launcher.shutdown();invoker.destroy();health.destroy();}
    }
    @Test void standaloneHostExecutesRawScriptsAndReapsChildrenAfterNormalExit()throws Exception {
        var processProperties=new com.sky.job.plugin.runtime.process.ProcessPluginRuntimeProperties(null,null,null,null,null,null,null,0,"java","256m",Path.of("missing-java-runner.jar"),1048576,temporary,temporary,temporary.resolve("logs"));
        var settings=mock(com.sky.job.system.DynamicProcessPluginRuntimeSettings.class);
        when(settings.startupTimeout()).thenReturn(Duration.ofSeconds(10));when(settings.taskTimeout()).thenReturn(Duration.ofSeconds(10));when(settings.stopTimeout()).thenReturn(Duration.ofSeconds(1));
        var health=new com.sky.job.plugin.runtime.process.ProcessPluginHealthChecker(processProperties);
        var tokens=new com.sky.job.plugin.security.PluginRuntimeTokenRegistry();
        var validator=new com.sky.job.plugin.runtime.process.ProcessPluginRuntimeValidator(processProperties,new com.sky.job.plugin.artifact.dependency.PluginDependencyResolutionProperties(temporary,null,0,0,false));
        var launcher=new ScriptRuntimeLauncher(properties,service,new ScriptArchive(new ObjectMapper()),new com.sky.job.plugin.runtime.process.ProcessPluginPortAllocator(),validator,health,processProperties,settings,tokens);
        var invoker=new com.sky.job.execution.PluginTaskInvoker(1048576);
        try {
            success(change("node","install","is-number","7.0.0"));success(change("python","install","six","1.17.0"));
            for(String extension:List.of(".js",".mjs",".py")) {
                String engine=extension.equals(".py")?"python":"node";
                String code=extension.equals(".py") ? "import subprocess,sys,os,six\nif __name__=='__main__':\n assert six.__version__=='1.17.0'\n assert os.getenv('dxqy')=='中文@#'\n assert sys.argv[1]=='a b & $()'\n assert 'SKY_PLUGIN_TOKEN' not in os.environ\n child=subprocess.Popen([sys.executable,'-c','import time;time.sleep(60)'])\n print('CHILD:'+str(child.pid),flush=True)\n" :
                        (extension.equals(".js")?"const {spawn}=require('node:child_process');const number=require('is-number');":"import {spawn} from 'node:child_process';import number from 'is-number';")+
                        "if(!number(3)||process.env.dxqy!=='中文@#'||process.argv[2]!=='a b & $()'||process.env.SKY_PLUGIN_TOKEN)process.exit(5);const child=spawn(process.execPath,['-e','setInterval(()=>{},1000)'],{stdio:'ignore'});child.unref();console.log('CHILD:'+child.pid);";
                String id=UUID.randomUUID().toString();Path artifact=temporary.resolve(id+extension);Files.writeString(artifact,code);
                var spec=new com.sky.job.plugin.runtime.PluginRuntimeSpec("installation","test",id,"",artifact,com.sky.job.plugin.artifact.Sha256Digests.digestHex(artifact),temporary.resolve("work").resolve(id),19090,List.of(),Set.of(com.sky.job.plugin.security.PluginCapability.TASK),false,5005,false,id,1L,1L,engine,"main"+extension,"standalone");
                var handle=launcher.start(spec);
                try {
                    var options=com.sky.plugin.protocol.StandaloneTaskOptions.newBuilder().addArguments("a b & $()").putEnvironment("dxqy","中文@#").build();
                    var response=invoker.invoke(handle,com.sky.plugin.protocol.TaskRunRequest.newBuilder().setPluginId("test").setRequestId(id).setTimeoutMillis(5000).setStandaloneOptions(options).build(),Duration.ofSeconds(7));
                    assertThat(response.getStatus()).withFailMessage("%s\n%s",response,Files.readString(handle.logPath())).isEqualTo(com.sky.plugin.protocol.TaskRunStatus.TASK_RUN_STATUS_SUCCESS);
                    assertThat(new ObjectMapper().readTree(response.getResultJson()).path("exitCode").asInt(-1)).isZero();
                } finally {((ScriptRuntimeLauncher.Handle)handle).stop(Duration.ofSeconds(1));}
                String output=Files.readString(handle.logPath());var matcher=java.util.regex.Pattern.compile("CHILD:(\\d+)").matcher(output);assertThat(matcher.find()).isTrue();
                long pid=Long.parseLong(matcher.group(1));
                for(int i=0;i<50&&ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);i++)Thread.sleep(20);
                assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
                var lease=service.acquire(engine);try{assertThat(lease.path().resolve("runs").resolve(id)).doesNotExist();}finally{service.release(lease);}
            }
        } finally {launcher.shutdown();invoker.destroy();health.destroy();}
    }

    private com.sky.job.plugin.runtime.PluginRuntimeSpec spec(String engine,String code)throws Exception {
        String id=UUID.randomUUID().toString(),entry=engine.equals("node")?"main.mjs":"main.py";
        Path artifact=temporary.resolve(id+".zip");
        try(var zip=new java.util.zip.ZipOutputStream(Files.newOutputStream(artifact))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("sky-script.json"));zip.write(("{\"formatVersion\":1,\"pluginId\":\"test\",\"version\":\"1\",\"runtime\":{\"engine\":\""+engine+"\",\"entry\":\""+entry+"\"},\"capabilities\":{\"task\":true}}").getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry(entry));zip.write(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
        }
        return new com.sky.job.plugin.runtime.PluginRuntimeSpec("installation","test",id,"",artifact,com.sky.job.plugin.artifact.Sha256Digests.digestHex(artifact),temporary.resolve("work").resolve(id),19090,List.of(),Set.of(com.sky.job.plugin.security.PluginCapability.TASK),false,5005,false,id,1L,1L,engine,entry);
    }
}
