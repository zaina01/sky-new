package com.sky.job.audit.dto;

import com.sky.job.audit.SystemLog;

import java.time.LocalDateTime;

public record SystemLogResponse(
        Long id,
        String module,
        String action,
        String level,
        String status,
        String operatorUserId,
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
    public static SystemLogResponse from(SystemLog log) {
        return new SystemLogResponse(
                log.id(),
                log.module(),
                log.action(),
                log.level(),
                log.status(),
                log.operatorUserId(),
                log.operatorName(),
                log.sourceType(),
                log.requestUri(),
                log.requestMethod(),
                log.ip(),
                log.userAgent(),
                log.targetType(),
                log.targetId(),
                log.message(),
                log.detail(),
                log.remark(),
                log.createTime()
        );
    }
}
