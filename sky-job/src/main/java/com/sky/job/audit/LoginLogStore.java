package com.sky.job.audit;

import com.sky.job.audit.mapper.LoginLogMapper;
import com.sky.job.security.EffectiveDataScope;
import com.sky.job.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class LoginLogStore {
    private static final int MAX_FAIL_REASON_LENGTH = 500;

    private final LoginLogMapper mapper;

    public LoginLogStore(LoginLogMapper mapper) {
        this.mapper = mapper;
    }

    public void success(User user, HttpServletRequest request) {
        insert(user.userId(), user.departmentId(), user.username(), "SUCCESS", null, request);
    }

    public void failure(String username, String reason, HttpServletRequest request) {
        insert(null, null, username, "FAILED", reason, request);
    }

    public List<LoginLog> list(
            String username,
            String userId,
            String status,
            String loginIp,
            String deviceType,
            LocalDateTime startTime,
            LocalDateTime endTime,
            EffectiveDataScope scope,
            Integer page,
            Integer size
    ) {
        return mapper.list(trim(username), like(username), trim(userId), trim(status), trim(loginIp), trim(deviceType), startTime, endTime, scope, normalizedSize(size), (normalizedPage(page) - 1) * normalizedSize(size));
    }

    public long count(
            String username,
            String userId,
            String status,
            String loginIp,
            String deviceType,
            LocalDateTime startTime,
            LocalDateTime endTime,
            EffectiveDataScope scope
    ) {
        return mapper.count(trim(username), like(username), trim(userId), trim(status), trim(loginIp), trim(deviceType), startTime, endTime, scope);
    }

    public long countOlderThan(LocalDateTime cutoff) {
        return mapper.countOlderThan(cutoff);
    }

    public int deleteOlderThan(LocalDateTime cutoff, int limit) {
        return mapper.deleteOlderThan(cutoff, limit);
    }

    private void insert(String userId, Long ownerDepartmentId, String username, String status, String failReason, HttpServletRequest request) {
        String userAgent = userAgent(request);
        String clientIp = clientIp(request);
        mapper.insert(userId, ownerDepartmentId, username, status, deviceType(userAgent), clientIp, ipType(clientIp), userAgent, limit(failReason, MAX_FAIL_REASON_LENGTH), request.getRequestURI(), request.getMethod());
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

    private String deviceType(String userAgent) {
        String lower = userAgent.toLowerCase();
        if (lower.contains("mobile") || lower.contains("android") || lower.contains("iphone")) return "MOBILE";
        if (lower.contains("tablet") || lower.contains("ipad")) return "TABLET";
        if (lower.isBlank()) return "UNKNOWN";
        return "DESKTOP";
    }

    private String ipType(String ip) {
        if (ip == null || ip.isBlank()) return "UNKNOWN";
        if (ip.equals("127.0.0.1") || ip.equals("::1")) return "LOOPBACK";
        if (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) return "PRIVATE";
        return "PUBLIC";
    }

    private String limit(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
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
