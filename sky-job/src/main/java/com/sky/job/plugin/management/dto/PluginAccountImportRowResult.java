package com.sky.job.plugin.management.dto;

public record PluginAccountImportRowResult(
        int rowNumber,
        String accountId,
        String status,
        String operation,
        String message
) {
}
