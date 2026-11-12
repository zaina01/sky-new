package com.sky.job.audit;

import com.sky.job.audit.dto.LoginLogResponse;
import com.sky.job.audit.dto.SystemLogResponse;
import com.sky.job.scheduler.dto.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.sky.job.security.AuthenticatedUser;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/system")
public class AuditLogController {
    private final AuditLogQueryService queryService;

    public AuditLogController(AuditLogQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/login-logs")
    public PageResponse<LoginLogResponse> listLoginLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String loginIp,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return queryService.listLoginLogs(username, userId, status, loginIp, deviceType, startTime, endTime, page, size, user);
    }

    @GetMapping("/logs")
    public PageResponse<SystemLogResponse> listSystemLogs(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String operatorUserId,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return queryService.listSystemLogs(module, action, level, status, operatorUserId, operatorName, sourceType, targetType, targetId, startTime, endTime, page, size, user);
    }
}
