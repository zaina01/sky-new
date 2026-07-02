package com.sky.job.audit;

import java.time.LocalDateTime;

public record LoginLog(
        Long id,
        String userId,
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
}
