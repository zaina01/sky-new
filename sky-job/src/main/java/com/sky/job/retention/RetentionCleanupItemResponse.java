package com.sky.job.retention;

import java.time.LocalDateTime;

public record RetentionCleanupItemResponse(
        RetentionCleanupCategory category,
        LocalDateTime cutoff,
        int retentionDays,
        boolean dryRun,
        long candidateCount,
        long deletedCount,
        String skippedReason
) {
}
