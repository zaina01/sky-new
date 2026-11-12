package com.sky.job.plugin.storage;

import com.sky.job.plugin.storage.dto.PluginAuditLogResponse;
import com.sky.job.scheduler.dto.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.sky.job.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/plugins/audit-logs")
public class PluginAuditLogController {
    private final PluginAuditQueryService queryService;

    public PluginAuditLogController(PluginAuditQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public PageResponse<PluginAuditLogResponse> list(
            @RequestParam(required = false) String pluginId,
            @RequestParam(required = false) String installationId,
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return queryService.listAuditLogs(installationId, pluginId, instanceId, actor, action, startTime, endTime, page, size, user);
    }
}
