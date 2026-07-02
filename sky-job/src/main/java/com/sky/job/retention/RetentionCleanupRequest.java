package com.sky.job.retention;

import java.util.Set;

public record RetentionCleanupRequest(
        Boolean dryRun,
        Boolean confirm,
        Set<RetentionCleanupCategory> categories
) {
}
