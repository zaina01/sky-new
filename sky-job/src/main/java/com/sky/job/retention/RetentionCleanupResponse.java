package com.sky.job.retention;

import java.time.LocalDateTime;
import java.util.List;

public record RetentionCleanupResponse(
        boolean enabled,
        boolean dryRun,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        long totalCandidateCount,
        long totalDeletedCount,
        List<RetentionCleanupItemResponse> items
) {
}
