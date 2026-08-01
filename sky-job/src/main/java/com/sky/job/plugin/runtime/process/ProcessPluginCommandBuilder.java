package com.sky.job.plugin.runtime.process;

import com.sky.job.plugin.runtime.PluginRuntimeSpec;
import com.sky.job.system.DynamicProcessPluginRuntimeSettings;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProcessPluginCommandBuilder {
    private final ProcessPluginRuntimeProperties properties;
    private final DynamicProcessPluginRuntimeSettings runtimeSettings;

    public ProcessPluginCommandBuilder(ProcessPluginRuntimeProperties properties, DynamicProcessPluginRuntimeSettings runtimeSettings) {
        this.properties = properties;
        this.runtimeSettings = runtimeSettings;
    }

    public List<String> build(PluginRuntimeSpec spec, int port) {
        List<String> command = new ArrayList<>();
        command.add(properties.javaCommand());
        command.add("-Dfile.encoding=UTF-8");
        command.add("-Dsun.stdout.encoding=UTF-8");
        command.add("-Dsun.stderr.encoding=UTF-8");
        command.add("-Dorg.slf4j.simpleLogger.showDateTime=true");
        command.add("-Dorg.slf4j.simpleLogger.dateTimeFormat=yyyy-MM-dd HH:mm:ss.SSS");
        command.add("-Xmx" + runtimeSettings.maxHeap());
        if (spec.remoteDebugEnabled()) {
            String suspend = spec.remoteDebugSuspend() ? "y" : "n";
            command.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=" + suspend + ",address=127.0.0.1:" + spec.remoteDebugPort());
        }
        command.add("-jar");
        command.add(properties.runnerJar().toAbsolutePath().normalize().toString());
        command.add(spec.artifactPath().toAbsolutePath().normalize().toString());
        spec.dependencyPaths().forEach(path -> command.add(path.toAbsolutePath().normalize().toString()));
        return List.copyOf(command);
    }
}
