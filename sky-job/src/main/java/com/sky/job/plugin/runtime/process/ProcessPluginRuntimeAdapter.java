package com.sky.job.plugin.runtime.process;

import com.sky.job.plugin.runtime.PluginRuntimeAdapter;
import com.sky.job.plugin.runtime.PluginRuntimeHandle;
import com.sky.job.plugin.runtime.PluginRuntimeSpec;
import com.sky.job.plugin.runtime.PluginRuntimeState;
import com.sky.job.plugin.security.PluginRuntimeTokenRegistry;
import com.sky.job.system.DynamicProcessPluginRuntimeSettings;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@EnableConfigurationProperties(ProcessPluginRuntimeProperties.class)
public class ProcessPluginRuntimeAdapter implements PluginRuntimeAdapter {
    private final ProcessPluginPortAllocator portAllocator;
    private final ProcessPluginCommandBuilder commandBuilder;
    private final ProcessPluginRuntimeValidator validator;
    private final ProcessPluginHealthChecker healthChecker;
    private final ProcessPluginRuntimeProperties properties;
    private final DynamicProcessPluginRuntimeSettings runtimeSettings;
    private final PluginRuntimeTokenRegistry tokenRegistry;

    public ProcessPluginRuntimeAdapter(
            ProcessPluginPortAllocator portAllocator,
            ProcessPluginCommandBuilder commandBuilder,
            ProcessPluginRuntimeValidator validator,
            ProcessPluginHealthChecker healthChecker,
            ProcessPluginRuntimeProperties properties,
            DynamicProcessPluginRuntimeSettings runtimeSettings,
            PluginRuntimeTokenRegistry tokenRegistry
    ) {
        this.portAllocator = portAllocator;
        this.commandBuilder = commandBuilder;
        this.validator = validator;
        this.healthChecker = healthChecker;
        this.properties = properties;
        this.runtimeSettings = runtimeSettings;
        this.tokenRegistry = tokenRegistry;
    }

    @Override
    public PluginRuntimeHandle start(PluginRuntimeSpec spec) {
        validator.validate(spec);
        String token = tokenRegistry.register(
                spec.installationId(),
                spec.pluginId(),
                spec.instanceId(),
                spec.jobId(),
                spec.executionId(),
                spec.capabilities(),
                tokenTtl(spec)
        );
        Process process = null;
        try {
            Files.createDirectories(spec.workDir());
            int port = portAllocator.allocate();
            Path logFile = validator.logFile(spec, spec.runId());
            ProcessBuilder processBuilder = new ProcessBuilder(commandBuilder.build(spec, port));
            processBuilder.directory(spec.workDir().toFile());
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
            Map<String, String> environment = processBuilder.environment();
            environment.clear();
            environment.put("SKY_PLUGIN_INSTALLATION_ID", spec.installationId());
            environment.put("SKY_PLUGIN_ID", spec.pluginId());
            environment.put("SKY_PLUGIN_INSTANCE_ID", spec.instanceId());
            environment.put("SKY_PLUGIN_MAIN_CLASS", spec.mainClass());
            environment.put("SKY_PLUGIN_PORT", Integer.toString(port));
            environment.put("SKY_PLUGIN_GRPC_BIND_HOST", "127.0.0.1");
            environment.put("SKY_PLUGIN_GRPC_MAX_INBOUND_MESSAGE_BYTES", Integer.toString(properties.maxInboundMessageBytes()));
            environment.put("SKY_PLUGIN_MAX_TASK_TIMEOUT_MILLIS", Long.toString(runtimeSettings.taskTimeout().toMillis()));
            environment.put("SKY_HOST_GRPC_HOST", "127.0.0.1");
            environment.put("SKY_HOST_GRPC_PORT", Integer.toString(spec.hostGrpcPort()));
            environment.put("SKY_PLUGIN_TOKEN", token);
            environment.put("SKY_PLUGIN_WORK_DIR", spec.workDir().toAbsolutePath().normalize().toString());
            process = processBuilder.start();
            ProcessPluginRuntimeHandle handle = new ProcessPluginRuntimeHandle(spec.installationId(), spec.pluginId(), spec.instanceId(), port, token, logFile, process);
            healthChecker.awaitHealthy(handle, runtimeSettings.startupTimeout());
            return handle;
        } catch (IOException | RuntimeException exception) {
            if (process != null) {
                process.destroyForcibly();
            }
            tokenRegistry.revoke(spec.pluginId(), spec.instanceId());
            throw new IllegalStateException("Failed to start plugin process: " + spec.pluginId(), exception);
        }
    }

    private Duration tokenTtl(PluginRuntimeSpec spec) {
        Duration taskTtl = runtimeSettings.startupTimeout().plus(runtimeSettings.taskTimeout());
        if (!spec.capabilities().contains(com.sky.job.plugin.security.PluginCapability.SERVICE)) {
            return taskTtl;
        }
        Duration serviceTtl = runtimeSettings.serviceIdleTimeout().plus(runtimeSettings.serviceIdleScanInterval()).plus(runtimeSettings.stopTimeout());
        return serviceTtl.compareTo(taskTtl) > 0 ? serviceTtl : taskTtl;
    }

    @Override
    public void stop(PluginRuntimeHandle handle, Duration timeout) {
        try {
            if (!(handle instanceof ProcessPluginRuntimeHandle processHandle)) {
                return;
            }
            Process process = processHandle.process();
            process.destroy();
            try {
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                }
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        } finally {
            tokenRegistry.revoke(handle.pluginId(), handle.instanceId());
        }
    }

    @Override
    public PluginRuntimeState inspect(PluginRuntimeHandle handle) {
        if (handle instanceof ProcessPluginRuntimeHandle processHandle && processHandle.process().isAlive()) {
            return PluginRuntimeState.RUNNING;
        }
        return PluginRuntimeState.STOPPED;
    }
}
