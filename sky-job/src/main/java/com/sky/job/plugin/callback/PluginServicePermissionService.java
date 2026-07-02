package com.sky.job.plugin.callback;

import com.sky.job.plugin.callback.dto.PluginServicePermissionResponse;
import com.sky.job.plugin.callback.dto.UpdatePluginServicePermissionStateRequest;
import com.sky.job.plugin.callback.dto.UpsertPluginServicePermissionRequest;
import com.sky.job.plugin.security.PluginCapability;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.job.scheduler.dto.PageResponse;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class PluginServicePermissionService {
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private final PluginServicePermissionStore permissionStore;
    private final PluginRuntimeMetadataStore runtimeStore;

    public PluginServicePermissionService(PluginServicePermissionStore permissionStore, PluginRuntimeMetadataStore runtimeStore) {
        this.permissionStore = permissionStore;
        this.runtimeStore = runtimeStore;
    }

    public PageResponse<PluginServicePermissionResponse> list(String callerPluginId, String targetPluginId, String operation, Boolean enabled, Integer page, Integer size) {
        return new PageResponse<>(
                permissionStore.list(callerPluginId, targetPluginId, operation, enabled, page, size).stream().map(PluginServicePermissionResponse::from).toList(),
                normalizedPage(page),
                normalizedSize(size),
                permissionStore.count(callerPluginId, targetPluginId, operation, enabled)
        );
    }

    public PluginServicePermissionResponse get(long id) {
        return permissionStore.findById(id).map(PluginServicePermissionResponse::from).orElseThrow(() -> new IllegalArgumentException("Plugin service permission not found"));
    }

    public PluginServicePermissionResponse upsert(UpsertPluginServicePermissionRequest request) {
        String callerPluginId = requiredName(request == null ? null : request.callerPluginId(), "Invalid caller plugin id");
        String targetPluginId = requiredName(request.targetPluginId(), "Invalid target plugin id");
        String operation = requiredName(request.operation(), "Invalid plugin service operation");
        validateRuntime(callerPluginId, false);
        validateRuntime(targetPluginId, true);
        permissionStore.upsert(callerPluginId, targetPluginId, operation, request.enabled() == null || request.enabled());
        return permissionStore.list(callerPluginId, targetPluginId, operation, null, 1, 1).stream()
                .findFirst()
                .map(PluginServicePermissionResponse::from)
                .orElseThrow(() -> new IllegalStateException("Plugin service permission was not saved"));
    }

    public PluginServicePermissionResponse updateEnabled(long id, UpdatePluginServicePermissionStateRequest request) {
        permissionStore.findById(id).orElseThrow(() -> new IllegalArgumentException("Plugin service permission not found"));
        if (permissionStore.updateEnabled(id, request.enabled()) == 0) {
            throw new IllegalArgumentException("Plugin service permission not found");
        }
        return get(id);
    }

    public void delete(long id) {
        permissionStore.findById(id).orElseThrow(() -> new IllegalArgumentException("Plugin service permission not found"));
        if (permissionStore.delete(id) == 0) {
            throw new IllegalArgumentException("Plugin service permission not found");
        }
    }

    private void validateRuntime(String pluginId, boolean requireService) {
        var runtime = runtimeStore.findRuntimeByPluginId(pluginId).orElseThrow(() -> new IllegalArgumentException("Plugin runtime not found: " + pluginId));
        if (requireService && !runtimeStore.runtimeCapabilities(runtime.capabilitiesJson()).contains(PluginCapability.SERVICE)) {
            throw new IllegalArgumentException("Target plugin does not support service calls");
        }
    }

    private String requiredName(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (!NAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private int normalizedPage(Integer page) {
        return Math.max(page == null ? 1 : page, 1);
    }

    private int normalizedSize(Integer size) {
        return Math.min(Math.max(size == null ? 20 : size, 1), 100);
    }
}
