package com.sky.job.plugin.callback;

import com.sky.job.plugin.callback.dto.PluginServicePermissionResponse;
import com.sky.job.plugin.callback.dto.BatchUpsertPluginServicePermissionRequest;
import com.sky.job.plugin.callback.dto.UpdatePluginServicePermissionStateRequest;
import com.sky.job.plugin.callback.dto.UpsertPluginServicePermissionRequest;
import com.sky.job.scheduler.dto.PageResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.sky.job.security.AuthenticatedUser;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/plugin-service/permissions")
public class PluginServicePermissionController {
    private final PluginServicePermissionService service;

    public PluginServicePermissionController(PluginServicePermissionService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PluginServicePermissionResponse> list(
            @RequestParam(required = false) String callerInstallationId,
            @RequestParam(required = false) String callerPluginId,
            @RequestParam(required = false) String targetInstallationId,
            @RequestParam(required = false) String targetPluginId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.list(callerInstallationId, callerPluginId, targetInstallationId, targetPluginId, operation, enabled, page, size, user);
    }

    @GetMapping("/{id}")
    public PluginServicePermissionResponse get(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.get(id, user);
    }

    @PostMapping
    public PluginServicePermissionResponse upsert(@RequestBody UpsertPluginServicePermissionRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.upsert(request, user);
    }

    @PostMapping("/batch")
    public List<PluginServicePermissionResponse> upsertBatch(@RequestBody BatchUpsertPluginServicePermissionRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.upsertBatch(request, user);
    }

    @PatchMapping("/{id}/enabled")
    public PluginServicePermissionResponse updateEnabled(@PathVariable long id, @RequestBody UpdatePluginServicePermissionStateRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.updateEnabled(id, request, user);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user) {
        service.delete(id, user);
        return Map.of("success", true);
    }
}
