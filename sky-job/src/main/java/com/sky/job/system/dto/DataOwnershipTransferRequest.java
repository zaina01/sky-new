package com.sky.job.system.dto;

import java.util.List;

public record DataOwnershipTransferRequest(
        String targetUserId,
        Long targetDepartmentId,
        List<String> pluginInstallationIds,
        List<Long> jobIds,
        List<String> notificationChannelIds,
        List<String> notificationRuleIds
) {
}
