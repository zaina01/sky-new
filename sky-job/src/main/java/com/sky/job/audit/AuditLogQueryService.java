package com.sky.job.audit;

import com.sky.job.audit.dto.LoginLogResponse;
import com.sky.job.audit.dto.SystemLogResponse;
import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.security.AuthenticatedUser;
import com.sky.job.security.DataScopeService;
import com.sky.job.security.EffectiveDataScope;
import com.sky.job.security.PermissionNames;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditLogQueryService {
    private final LoginLogStore loginLogStore;
    private final SystemLogStore systemLogStore;
    private final DataScopeService dataScopeService;

    public AuditLogQueryService(LoginLogStore loginLogStore, SystemLogStore systemLogStore, DataScopeService dataScopeService) {
        this.loginLogStore = loginLogStore;
        this.systemLogStore = systemLogStore;
        this.dataScopeService = dataScopeService;
    }

    public PageResponse<LoginLogResponse> listLoginLogs(
            String username,
            String userId,
            String status,
            String loginIp,
            String deviceType,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer page,
            Integer size,
            AuthenticatedUser user
    ) {
        validateTimeRange(startTime, endTime);
        EffectiveDataScope scope = dataScopeService.resolve(user, PermissionNames.SYSTEM_LOGIN_LOG_READ);
        return new PageResponse<>(
                loginLogStore.list(username, userId, status, loginIp, deviceType, startTime, endTime, scope, page, size).stream().map(LoginLogResponse::from).toList(),
                normalizedPage(page),
                normalizedSize(size),
                loginLogStore.count(username, userId, status, loginIp, deviceType, startTime, endTime, scope)
        );
    }

    public PageResponse<SystemLogResponse> listSystemLogs(
            String module,
            String action,
            String level,
            String status,
            String operatorUserId,
            String operatorName,
            String sourceType,
            String targetType,
            String targetId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer page,
            Integer size,
            AuthenticatedUser user
    ) {
        validateTimeRange(startTime, endTime);
        EffectiveDataScope scope = dataScopeService.resolve(user, PermissionNames.SYSTEM_LOG_READ);
        return new PageResponse<>(
                systemLogStore.list(module, action, level, status, operatorUserId, operatorName, sourceType, targetType, targetId, startTime, endTime, scope, page, size).stream().map(SystemLogResponse::from).toList(),
                normalizedPage(page),
                normalizedSize(size),
                systemLogStore.count(module, action, level, status, operatorUserId, operatorName, sourceType, targetType, targetId, startTime, endTime, scope)
        );
    }

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("Invalid time range");
        }
    }

    private int normalizedPage(Integer page) {
        return Math.max(page == null ? 1 : page, 1);
    }

    private int normalizedSize(Integer size) {
        return Math.min(Math.max(size == null ? 20 : size, 1), 100);
    }
}
