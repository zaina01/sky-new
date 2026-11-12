package com.sky.job.plugin.management.dto;

import java.util.List;

public record PluginAccountImportResponse(
        String installationId,
        boolean dryRun,
        int totalRows,
        int successRows,
        int failedRows,
        int createdRows,
        int updatedRows,
        List<PluginAccountImportRowResult> rows
) {
}
