package com.sky.job.plugin.callback;

import com.sky.job.plugin.callback.dto.PluginServicePermissionResponse;
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

import java.util.Map;

@RestController
@RequestMapping("/plugin-service/permissions")
public class PluginServicePermissionController {
    private final PluginServicePermissionService service;

    public PluginServicePermissionController(PluginServicePermissionService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PluginServicePermissionResponse> list(
            @RequestParam(required = false) String callerPluginId,
            @RequestParam(required = false) String targetPluginId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return service.list(callerPluginId, targetPluginId, operation, enabled, page, size);
    }

    @GetMapping("/{id}")
    public PluginServicePermissionResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public PluginServicePermissionResponse upsert(@RequestBody UpsertPluginServicePermissionRequest request) {
        return service.upsert(request);
    }

    @PatchMapping("/{id}/enabled")
    public PluginServicePermissionResponse updateEnabled(@PathVariable long id, @RequestBody UpdatePluginServicePermissionStateRequest request) {
        return service.updateEnabled(id, request);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable long id) {
        service.delete(id);
        return Map.of("success", true);
    }
}
