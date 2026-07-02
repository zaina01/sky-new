package com.sky.job.plugin.runtime.dto;

import com.sky.job.plugin.runtime.PluginRuntimeRecord;
import com.sky.job.plugin.runtime.PluginRuntimeState;

import java.time.LocalDateTime;

public record PluginRuntimeResponse(
        long id,
        String pluginId,
        String instanceId,
        String version,
        String displayName,
        String mainClass,
        String artifactPath,
        String artifactSha256,
        String runtimeType,
        PluginRuntimeState state,
        String runnerHost,
        Integer runnerPort,
        String workDir,
        String capabilitiesJson,
        String configSchemaJson,
        String accountSchemaJson,
        boolean managedByCurrentHost,
        PluginRuntimeState liveState,
        LocalDateTime startedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PluginRuntimeResponse from(PluginRuntimeRecord record, boolean managedByCurrentHost, PluginRuntimeState liveState) {
        return new PluginRuntimeResponse(
                record.id(),
                record.pluginId(),
                record.instanceId(),
                record.version(),
                record.displayName(),
                record.mainClass(),
                record.artifactPath(),
                record.artifactSha256(),
                record.runtimeType(),
                record.state(),
                record.runnerHost(),
                record.runnerPort(),
                record.workDir(),
                record.capabilitiesJson(),
                record.configSchemaJson(),
                record.accountSchemaJson(),
                managedByCurrentHost,
                liveState,
                record.startedAt(),
                record.createdAt(),
                record.updatedAt()
        );
    }
}
