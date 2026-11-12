package com.sky.job.audit.dto;

import com.sky.job.audit.LoginLog;

import java.time.LocalDateTime;

public record LoginLogResponse(
        Long id,
        String userId,
        Long ownerDepartmentId,
        String username,
        String status,
        LocalDateTime loginTime,
        String deviceType,
        String loginIp,
        String ipType,
        String userAgent,
        String failReason,
        String requestUri,
        String requestMethod,
        String remark
) {
    public static LoginLogResponse from(LoginLog log) {
        return new LoginLogResponse(
                log.id(),
                log.userId(),
                log.ownerDepartmentId(),
                log.username(),
                log.status(),
                log.loginTime(),
                log.deviceType(),
                log.loginIp(),
                log.ipType(),
                log.userAgent(),
                log.failReason(),
                log.requestUri(),
                log.requestMethod(),
                log.remark()
        );
    }
}
