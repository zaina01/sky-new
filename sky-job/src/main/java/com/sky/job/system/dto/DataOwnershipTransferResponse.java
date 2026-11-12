package com.sky.job.system.dto;

public record DataOwnershipTransferResponse(
        String targetUserId,
        String targetUsername,
        Long targetDepartmentId,
        String targetDepartmentName,
        int pluginInstallationsTransferred,
        int jobsTransferred,
        int notificationChannelsTransferred,
        int notificationRulesTransferred
) {
}
