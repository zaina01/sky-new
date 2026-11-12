package com.sky.job.audit;

import java.time.LocalDateTime;

public record SystemLog(
        Long id,
        String module,
        String action,
        String level,
        String status,
        String operatorUserId,
        Long ownerDepartmentId,
        String operatorName,
        String sourceType,
        String requestUri,
        String requestMethod,
        String ip,
        String userAgent,
        String targetType,
        String targetId,
        String message,
        String detail,
        String remark,
        LocalDateTime createTime
) {
}
