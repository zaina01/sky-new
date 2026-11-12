package com.sky.job.plugin.management.dto;

public record UpsertPluginAccountRequest(
        String accountId,
        String displayName,
        String accountJson,
        Boolean enabled,
        Long groupId
) {
}
