package com.sky.job.plugin.callback.dto;

import java.util.List;

public record BatchUpsertPluginServicePermissionRequest(
        String callerInstallationId,
        String targetInstallationId,
        List<String> operations,
        Boolean enabled
) {
}
