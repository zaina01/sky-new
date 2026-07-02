package com.sky.plugin.api;

import java.util.List;

public record NotificationSendResult(
        boolean success,
        String message,
        List<Receipt> receipts
) {
    public NotificationSendResult {
        receipts = receipts == null ? List.of() : List.copyOf(receipts);
    }

    public record Receipt(
            String channelId,
            String status,
            String message,
            long historyId
    ) {
    }
}
