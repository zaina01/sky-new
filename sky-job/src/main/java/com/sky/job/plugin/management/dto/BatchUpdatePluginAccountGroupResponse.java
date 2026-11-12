package com.sky.job.plugin.management.dto;

import java.util.List;

public record BatchUpdatePluginAccountGroupResponse(
        String installationId,
        Long groupId,
        int total,
        int succeeded,
        int failed,
        List<BatchPluginAccountGroupReceipt> receipts
) {
}
