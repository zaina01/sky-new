package com.sky.job.plugin.management.dto;

import com.sky.job.plugin.storage.PluginAccount;

import java.time.LocalDateTime;

public record PluginAccountResponse(
        String installationId,
        String accountId,
        String displayName,
        String accountJson,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long groupId,
        String groupName
) {
    public static PluginAccountResponse from(PluginAccount account) {
        return new PluginAccountResponse(
                account.installationId(),
                account.accountId(),
                account.displayName(),
                account.accountJson(),
                account.enabled(),
                account.createdAt(),
                account.updatedAt(),
                account.groupId(),
                account.groupName()
        );
    }
}
