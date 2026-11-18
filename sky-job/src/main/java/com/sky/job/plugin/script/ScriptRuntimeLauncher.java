package com.sky.job.plugin.script;

import com.sky.job.plugin.runtime.*;
import com.sky.job.plugin.runtime.process.*;
import com.sky.job.plugin.security.PluginRuntimeTokenRegistry;
import com.sky.job.system.DynamicProcessPluginRuntimeSettings;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ScriptRuntimeLauncher {
    private final ScriptRuntimeProperties properties;
    private final ScriptEnvironmentService environments;
    private final ScriptArchive archives;
    private final ProcessPluginPortAllocator ports;
    private final ProcessPluginRuntimeValidator validator;
    private final ProcessPluginHealthChecker health;
    private final ProcessPluginRuntimeProperties processProperties;
    private final DynamicProcessPluginRuntimeSettings settings;
    private final PluginRuntimeTokenRegistry tokens;
    private final Semaphore slots;
    private final Set<Handle> handles=ConcurrentHashMap.newKeySet();
    private volatile boolean closing;
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(ScriptRuntimeLauncher.class);
    public ScriptRuntimeLauncher(ScriptRuntimeProperties properties,ScriptEnvironmentService environments,ScriptArchive archives,
            ProcessPluginPortAllocator ports,ProcessPluginRuntimeValidator validator,ProcessPluginHealthChecker health,
            ProcessPluginRuntimeProperties processProperties,DynamicProcessPluginRuntimeSettings settings,PluginRuntimeTokenRegistry tokens) {
        this.properties=properties;this.environments=environments;this.archives=archives;this.ports=ports;this.validator=validator;
        this.health=health;this.processProperties=processProperties;this.settings=settings;this.tokens=tokens;slots=new Semaphore(properties.maxProcesses());
    }
    public final class Handle implements PluginRuntimeHandle {
        private final ProcessPluginRuntimeHandle delegate;
        private final ScriptEnvironmentService.Lease lease;
        private final AtomicBoolean stopped=new AtomicBoolean();
        private final Set<ProcessHandle> children=ConcurrentHashMap.newKeySet();
        private final Thread watcher;
        private final Thread logs;
        private final Path runDirectory;
        private final WindowsProcessJob processJob;
        Handle(ProcessPluginRuntimeHandle delegate,ScriptEnvironmentService.Lease lease,Thread logs,Path runDirectory,WindowsProcessJob processJob) {
            this.processJob=processJob;
            this.delegate=delegate;this.lease=lease;this.logs=logs;this.runDirectory=runDirectory;
            watcher=Thread.ofVirtual().start(()->{
                while(!stopped.get() && delegate.process().isAlive()) {
                    delegate.process().descendants().forEach(children::add);
                    try { Thread.sleep(50); } catch(InterruptedException e) { return; }
                }
            });
        }
        public String installationId(){return delegate.installationId();} public String pluginId(){return delegate.pluginId();}
        public String instanceId(){return delegate.instanceId();} public int port(){return delegate.port();}
        public String token(){return delegate.token();} public Path logPath(){return delegate.logPath();}
        public String environmentRevision(){return lease.revision();} public String interpreterVersion(){return lease.interpreterVersion();}
        public boolean alive(){return delegate.process().isAlive();}
        public void stop(Duration timeout) {
            if(!stopped.compareAndSet(false,true)) return;
            watcher.interrupt();
            try {
                ScriptProcesses.stop(delegate.process(),timeout);
                if(processJob!=null)processJob.close();
                children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
                try { logs.join(5000); } catch(InterruptedException e) {Thread.currentThread().interrupt();}
            } finally {
                if(processJob!=null)processJob.close();
                handles.remove(this);tokens.revoke(pluginId(),instanceId());environments.release(lease);slots.release();
                if(!delegate.process().isAlive()) cleanupRun(runDirectory,lease.path());
            }
        }
    }
    @jakarta.annotation.PreDestroy
    void shutdown() {
        List<Handle> active;
        synchronized(handles) { closing=true;active=List.copyOf(handles); }
        active.forEach(handle->handle.stop(settings.stopTimeout()));
    }
    private static void cleanupRun(Path path,Path revision) {
        Path root=revision.toAbsolutePath().normalize().resolve("runs");
        Path target=path.toAbsolutePath().normalize();
        if(!target.startsWith(root) || target.equals(root))throw new IllegalArgumentException("Invalid script run cleanup path");
        try {
            if(Files.exists(target))try(var paths=Files.walk(target)){for(Path child:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(child);}
        } catch(java.io.IOException e){log.warn("Unable to remove script run copy: {}",target,e);}
    }
    public PluginRuntimeHandle start(PluginRuntimeSpec spec) {
        if(closing)throw new IllegalStateException("Host is shutting down");
        if(spec.executionId()==null) throw new IllegalArgumentException("Scripts can only run as tasks");
        validator.validate(spec);
        if(!slots.tryAcquire()) throw new IllegalStateException("Script process capacity exhausted");
        ScriptEnvironmentService.Lease lease=null;Process process=null;Handle handle=null;Path runDirectory=null;WindowsProcessJob processJob=null;
        try {
            lease=environments.acquire(spec.runtimeEngine());
            UUID.fromString(spec.runId());
            runDirectory=lease.path().resolve("runs").resolve(spec.runId());
            Path code=runDirectory.resolve("plugin");
            if("standalone".equals(spec.executionMode())) {
                if(!spec.entryPoint().matches("main\\.(js|mjs|py)"))throw new IllegalArgumentException("Invalid standalone entry");
                Files.createDirectories(code);
                Files.copy(spec.artifactPath(),code.resolve(spec.entryPoint()));
                if(spec.runtimeEngine().equals("node"))Files.writeString(code.resolve("package.json"),"{\"type\":\"commonjs\"}");
            } else archives.extract(spec.artifactPath(),code);
            Path entry=code.resolve(spec.entryPoint()).normalize();
            if(!entry.startsWith(code) || !Files.isRegularFile(entry)) throw new IllegalArgumentException("Script entry missing");
            Files.createDirectories(spec.workDir());
            int port=ports.allocate();
            Path log=validator.logFile(spec,spec.runId());
            String token=tokens.register(spec.installationId(),spec.pluginId(),spec.instanceId(),spec.jobId(),spec.executionId(),spec.capabilities(),settings.startupTimeout().plus(settings.taskTimeout()));
            List<String> command=spec.runtimeEngine().equals("node") ? List.of(properties.nodeCommand(),properties.nodeRunner().toAbsolutePath().toString(),entry.toString())
                    : List.of(environments.python(lease.path()).toString(),"-u",properties.pythonRunner().toAbsolutePath().toString(),entry.toString());
            ProcessBuilder builder=new ProcessBuilder(command).directory(spec.workDir().toFile()).redirectErrorStream(true);
            var env=builder.environment();ScriptProcesses.environment(env);
            env.put("SKY_PLUGIN_INSTALLATION_ID",spec.installationId());env.put("SKY_PLUGIN_ID",spec.pluginId());env.put("SKY_PLUGIN_INSTANCE_ID",spec.instanceId());
            env.put("SKY_PLUGIN_EXECUTION_MODE",spec.executionMode());
            env.put("SKY_PLUGIN_TOKEN",token);env.put("SKY_PLUGIN_PORT",Integer.toString(port));env.put("SKY_HOST_GRPC_PORT",Integer.toString(spec.hostGrpcPort()));
            env.put("SKY_HOST_GRPC_HOST","127.0.0.1");env.put("SKY_PLUGIN_GRPC_BIND_HOST","127.0.0.1");
            env.put("SKY_PLUGIN_MAX_TASK_TIMEOUT_MILLIS",Long.toString(settings.taskTimeout().toMillis()));
            env.put("SKY_PLUGIN_GRPC_MAX_INBOUND_MESSAGE_BYTES",Integer.toString(processProperties.maxInboundMessageBytes()));
            process=builder.start();
            processJob=WindowsProcessJob.attach(process);
            Thread logs=ScriptProcesses.drain(process,log,properties.maxLogBytes());
            var delegate=new ProcessPluginRuntimeHandle(spec.installationId(),spec.pluginId(),spec.instanceId(),port,token,log,process);
            handle=new Handle(delegate,lease,logs,runDirectory,processJob);
            synchronized(handles) {
                if(closing)throw new IllegalStateException("Host is shutting down");
                handles.add(handle);
            }
            // Reuse the authenticated gRPC health check used for Java processes.
            health.awaitHealthy(delegate,settings.startupTimeout());
            return handle;
        } catch(Exception e) {
            if(handle!=null) handle.stop(settings.stopTimeout());
            else {
                if(processJob!=null)processJob.close();
                if(process!=null)ScriptProcesses.stop(process,settings.stopTimeout());
                if(lease!=null){environments.release(lease);if(runDirectory!=null)cleanupRun(runDirectory,lease.path());}
                slots.release();tokens.revoke(spec.pluginId(),spec.instanceId());
            }
            throw new IllegalStateException("Failed to start "+spec.runtimeEngine()+" plugin: "+e.getMessage(),e);
        }
    }
}
