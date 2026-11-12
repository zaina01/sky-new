package com.sky.job.plugin.management.dto;

import java.util.List;

public record BatchUpdatePluginAccountStateResponse(
        String installationId,
        boolean enabled,
        int total,
        int succeeded,
        int failed,
        List<BatchPluginAccountStateReceipt> receipts
) {
}
