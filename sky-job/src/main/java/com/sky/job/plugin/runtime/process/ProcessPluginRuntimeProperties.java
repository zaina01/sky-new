package com.sky.job.plugin.runtime.process;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "sky.plugin.runtime.process")
public record ProcessPluginRuntimeProperties(
        Duration startupTimeout,
        Duration stopTimeout,
        Duration taskTimeout,
        Duration serviceCallTimeout,
        Duration maxServiceCallTimeout,
        Duration serviceIdleTimeout,
        Duration serviceIdleScanInterval,
        int maxRestarts,
        String javaCommand,
        String maxHeap,
        Path runnerJar,
        int maxInboundMessageBytes,
        Path artifactRoot,
        Path workRoot,
        Path logRoot
) {
    public ProcessPluginRuntimeProperties {
        if (startupTimeout == null) startupTimeout = Duration.ofSeconds(10);
        if (stopTimeout == null) stopTimeout = Duration.ofSeconds(5);
        if (taskTimeout == null) taskTimeout = Duration.ofSeconds(120);
        if (serviceCallTimeout == null) serviceCallTimeout = Duration.ofSeconds(10);
        if (maxServiceCallTimeout == null) maxServiceCallTimeout = Duration.ofSeconds(30);
        if (serviceIdleTimeout == null) serviceIdleTimeout = Duration.ofMinutes(10);
        if (serviceIdleScanInterval == null) serviceIdleScanInterval = Duration.ofMinutes(1);
        if (javaCommand == null || javaCommand.isBlank()) javaCommand = "java";
        if (maxHeap == null || maxHeap.isBlank()) maxHeap = "256m";
        if (runnerJar == null) runnerJar = Path.of("./sky-plugin-runner-java/build/libs/sky-plugin-runner-java-1.0-SNAPSHOT.jar");
        if (maxInboundMessageBytes <= 0) maxInboundMessageBytes = 1048576;
        if (artifactRoot == null) artifactRoot = Path.of("./data/plugins/artifacts");
        if (workRoot == null) workRoot = Path.of("./data/plugins/runtime");
        if (logRoot == null) logRoot = Path.of("./data/plugins/logs");
    }
}
