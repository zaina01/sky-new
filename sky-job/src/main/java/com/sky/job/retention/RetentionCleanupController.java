package com.sky.job.retention;

import com.sky.job.audit.SystemLogStore;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system/retention-cleanup")
public class RetentionCleanupController {
    private final RetentionCleanupService cleanupService;
    private final SystemLogStore systemLogStore;

    public RetentionCleanupController(RetentionCleanupService cleanupService, SystemLogStore systemLogStore) {
        this.cleanupService = cleanupService;
        this.systemLogStore = systemLogStore;
    }

    @PostMapping
    public RetentionCleanupResponse cleanup(@RequestBody(required = false) RetentionCleanupRequest request, HttpServletRequest httpRequest) {
        RetentionCleanupResponse response = cleanupService.runManual(request);
        systemLogStore.record("RETENTION", "RETENTION_CLEANUP", "INFO", "SUCCESS", "RETENTION", "manual", cleanupService.summary(response), httpRequest);
        return response;
    }
}
