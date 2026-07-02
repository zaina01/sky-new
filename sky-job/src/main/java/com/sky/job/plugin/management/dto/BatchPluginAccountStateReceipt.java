package com.sky.job.plugin.management.dto;

public record BatchPluginAccountStateReceipt(
        String accountId,
        boolean success,
        String message
) {
}
