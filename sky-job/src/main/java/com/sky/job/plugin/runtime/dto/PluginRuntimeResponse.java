package com.sky.job.plugin.runtime.dto;

import com.sky.job.plugin.runtime.PluginRuntimeRecord;
import com.sky.job.plugin.runtime.PluginRuntimeState;

import java.time.LocalDateTime;

public record PluginRuntimeResponse(
        long id,
        String installationId,
        String pluginId,
        String instanceId,
        String installationName,
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
        boolean remoteDebugEnabled,
        int remoteDebugPort,
        boolean remoteDebugSuspend,
        boolean managedByCurrentHost,
        PluginRuntimeState liveState,
        String ownerUserId,
        String ownerUsername,
        Long ownerDepartmentId,
        String ownerDepartmentName,
        LocalDateTime startedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PluginRuntimeResponse from(PluginRuntimeRecord record, boolean managedByCurrentHost, PluginRuntimeState liveState) {
        return new PluginRuntimeResponse(
                record.id(),
                record.installationId(),
                record.pluginId(),
                record.instanceId(),
                record.installationName(),
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
                record.remoteDebugEnabled(),
                record.remoteDebugPort(),
                record.remoteDebugSuspend(),
                managedByCurrentHost,
                liveState,
                record.ownerUserId(),
                record.ownerUsername(),
                record.ownerDepartmentId(),
                record.ownerDepartmentName(),
                record.startedAt(),
                record.createdAt(),
                record.updatedAt()
        );
    }
}
