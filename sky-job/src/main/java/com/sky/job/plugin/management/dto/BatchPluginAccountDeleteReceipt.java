package com.sky.job.plugin.management.dto;

public record BatchPluginAccountDeleteReceipt(
        String accountId,
        boolean success,
        String message
) {
}
