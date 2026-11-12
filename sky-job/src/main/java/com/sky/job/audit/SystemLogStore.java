package com.sky.job.audit;

import com.sky.job.audit.mapper.SystemLogMapper;
import com.sky.job.security.AuthenticatedUser;
import com.sky.job.security.EffectiveDataScope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class SystemLogStore {
    private final SystemLogMapper mapper;

    public SystemLogStore(SystemLogMapper mapper) {
        this.mapper = mapper;
    }

    public void record(String module, String action, String level, String status, String targetType, String targetId, String message, HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = null;
        Long ownerDepartmentId = null;
        String username = null;
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            userId = user.userId();
            ownerDepartmentId = user.departmentId();
            username = user.username();
        }
        mapper.insert(module, action, level, status, userId, ownerDepartmentId, username, username == null ? "SYSTEM" : "USER", request.getRequestURI(), request.getMethod(), clientIp(request), userAgent(request), targetType, targetId, message);
    }

    public void recordSystem(String module, String action, String level, String status, String targetType, String targetId, String message) {
        mapper.insertSystem(module, action, level, status, "SYSTEM", targetType, targetId, message);
    }

    public List<SystemLog> list(
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
            EffectiveDataScope scope,
            Integer page,
            Integer size
    ) {
        return mapper.list(trim(module), trim(action), trim(level), trim(status), trim(operatorUserId), trim(operatorName), like(operatorName), trim(sourceType), trim(targetType), trim(targetId), startTime, endTime, scope, normalizedSize(size), (normalizedPage(page) - 1) * normalizedSize(size));
    }

    public long count(
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
            EffectiveDataScope scope
    ) {
        return mapper.count(trim(module), trim(action), trim(level), trim(status), trim(operatorUserId), trim(operatorName), like(operatorName), trim(sourceType), trim(targetType), trim(targetId), startTime, endTime, scope);
    }

    public long countOlderThan(LocalDateTime cutoff) {
        return mapper.countOlderThan(cutoff);
    }

    public int deleteOlderThan(LocalDateTime cutoff, int limit) {
        return mapper.deleteOlderThan(cutoff, limit);
    }

    private String userAgent(HttpServletRequest request) {
        String value = request.getHeader("User-Agent");
        return value == null ? "" : value;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String like(String value) {
        return value == null || value.isBlank() ? null : "%" + value.trim() + "%";
    }

    private int normalizedPage(Integer page) {
        return Math.max(page == null ? 1 : page, 1);
    }

    private int normalizedSize(Integer size) {
        return Math.min(Math.max(size == null ? 20 : size, 1), 100);
    }
}
