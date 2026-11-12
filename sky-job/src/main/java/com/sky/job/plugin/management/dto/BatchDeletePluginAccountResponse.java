package com.sky.job.plugin.management.dto;

import java.util.List;

public record BatchDeletePluginAccountResponse(
        String installationId,
        int total,
        int succeeded,
        int failed,
        List<BatchPluginAccountDeleteReceipt> receipts
) {
}
