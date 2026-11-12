package com.sky.job.scheduler.mapper;

import java.time.LocalDateTime;

public record PluginRuntimeRow(
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
        String state,
        String runnerHost,
        Integer runnerPort,
        String workDir,
        String capabilitiesJson,
        String configSchemaJson,
        String accountSchemaJson,
        String dependencyJarsJson,
        boolean remoteDebugEnabled,
        int remoteDebugPort,
        boolean remoteDebugSuspend,
        String ownerUserId,
        String ownerUsername,
        Long ownerDepartmentId,
        String ownerDepartmentName,
        LocalDateTime startedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
