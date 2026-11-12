package com.sky.job.plugin.callback;

import com.sky.job.plugin.callback.dto.PluginServicePermissionResponse;
import com.sky.job.plugin.callback.dto.BatchUpsertPluginServicePermissionRequest;
import com.sky.job.plugin.callback.dto.UpdatePluginServicePermissionStateRequest;
import com.sky.job.plugin.callback.dto.UpsertPluginServicePermissionRequest;
import com.sky.job.plugin.runtime.PluginRuntimeRecord;
import com.sky.job.plugin.security.PluginCapability;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.security.AuthenticatedUser;
import com.sky.job.security.DataScopeService;
import com.sky.job.security.EffectiveDataScope;
import com.sky.job.security.PermissionNames;
import com.sky.job.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class PluginServicePermissionService {
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final int MAX_BATCH_OPERATIONS = 100;

    private final PluginServicePermissionStore permissionStore;
    private final PluginRuntimeMetadataStore runtimeStore;
    private final DataScopeService dataScopeService;

    public PluginServicePermissionService(
            PluginServicePermissionStore permissionStore,
            PluginRuntimeMetadataStore runtimeStore,
            DataScopeService dataScopeService
    ) {
        this.permissionStore = permissionStore;
        this.runtimeStore = runtimeStore;
        this.dataScopeService = dataScopeService;
    }

    public PageResponse<PluginServicePermissionResponse> list(
            String callerInstallationId,
            String callerPluginId,
            String targetInstallationId,
            String targetPluginId,
            String operation,
            Boolean enabled,
            Integer page,
            Integer size,
            AuthenticatedUser user
    ) {
        EffectiveDataScope scope = dataScopeService.resolve(user, PermissionNames.PLUGIN_SERVICE_PERMISSION_READ);
        return new PageResponse<>(
                permissionStore.list(callerInstallationId, callerPluginId, targetInstallationId, targetPluginId, operation, enabled, scope, page, size)
                        .stream().map(PluginServicePermissionResponse::from).toList(),
                normalizedPage(page),
                normalizedSize(size),
                permissionStore.count(callerInstallationId, callerPluginId, targetInstallationId, targetPluginId, operation, enabled, scope)
        );
    }

    public PluginServicePermissionResponse get(long id, AuthenticatedUser user) {
        return PluginServicePermissionResponse.from(scopedPermission(id, user, PermissionNames.PLUGIN_SERVICE_PERMISSION_READ));
    }

    public PluginServicePermissionResponse upsert(UpsertPluginServicePermissionRequest request, AuthenticatedUser user) {
        String callerInstallationId = requiredName(request == null ? null : request.callerInstallationId(), "Invalid caller installation id");
        String targetInstallationId = requiredName(request == null ? null : request.targetInstallationId(), "Invalid target installation id");
        String operation = requiredName(request == null ? null : request.operation(), "Invalid plugin service operation");
        EffectiveDataScope scope = dataScopeService.resolve(user, PermissionNames.PLUGIN_SERVICE_PERMISSION_WRITE);
        validateRuntime(callerInstallationId, false, scope);
        validateRuntime(targetInstallationId, true, scope);
        return savePermission(callerInstallationId, targetInstallationId, operation,
                request.enabled() == null || request.enabled(), scope);
    }

    @Transactional
    public List<PluginServicePermissionResponse> upsertBatch(BatchUpsertPluginServicePermissionRequest request, AuthenticatedUser user) {
        String callerInstallationId = requiredName(request == null ? null : request.callerInstallationId(), "Invalid caller installation id");
        String targetInstallationId = requiredName(request == null ? null : request.targetInstallationId(), "Invalid target installation id");
        if (request.operations() == null || request.operations().isEmpty()) {
            throw new IllegalArgumentException("At least one plugin service operation is required");
        }
        LinkedHashSet<String> operations = new LinkedHashSet<>();
        for (int i = 0; i < request.operations().size(); i++) {
            operations.add(requiredName(request.operations().get(i), "Invalid plugin service operation at index " + (i + 1)));
            if (operations.size() > MAX_BATCH_OPERATIONS) {
                throw new IllegalArgumentException("At most 100 distinct plugin service operations are allowed");
            }
        }
        EffectiveDataScope scope = dataScopeService.resolve(user, PermissionNames.PLUGIN_SERVICE_PERMISSION_WRITE);
        validateRuntime(callerInstallationId, false, scope);
        validateRuntime(targetInstallationId, true, scope);
        boolean enabled = request.enabled() == null || request.enabled();
        List<PluginServicePermissionResponse> saved = new ArrayList<>();
        for (String operation : operations) {
            saved.add(savePermission(callerInstallationId, targetInstallationId, operation, enabled, scope));
        }
        return saved;
    }

    private PluginServicePermissionResponse savePermission(String callerInstallationId, String targetInstallationId,
                                                           String operation, boolean enabled, EffectiveDataScope scope) {
        permissionStore.upsert(callerInstallationId, targetInstallationId, operation, enabled);
        return permissionStore.list(callerInstallationId, null, targetInstallationId, null, operation, null, scope, 1, 1).stream()
                .findFirst()
                .map(PluginServicePermissionResponse::from)
                .orElseThrow(() -> new IllegalStateException("Plugin service permission was not saved"));
    }

    public PluginServicePermissionResponse updateEnabled(long id, UpdatePluginServicePermissionStateRequest request, AuthenticatedUser user) {
        scopedPermission(id, user, PermissionNames.PLUGIN_SERVICE_PERMISSION_WRITE);
        if (request == null) {
            throw new IllegalArgumentException("Plugin service permission enabled state is required");
        }
        if (permissionStore.updateEnabled(id, request.enabled()) == 0) {
            throw new ResourceNotFoundException("Plugin service permission not found");
        }
        return PluginServicePermissionResponse.from(scopedPermission(id, user, PermissionNames.PLUGIN_SERVICE_PERMISSION_WRITE));
    }

    public void delete(long id, AuthenticatedUser user) {
        scopedPermission(id, user, PermissionNames.PLUGIN_SERVICE_PERMISSION_WRITE);
        if (permissionStore.delete(id) == 0) {
            throw new ResourceNotFoundException("Plugin service permission not found");
        }
    }

    private PluginServicePermission scopedPermission(long id, AuthenticatedUser user, String permissionName) {
        EffectiveDataScope scope = dataScopeService.resolve(user, permissionName);
        return permissionStore.findById(id, scope)
                .orElseThrow(() -> new ResourceNotFoundException("Plugin service permission not found"));
    }

    private PluginRuntimeRecord validateRuntime(String installationId, boolean requireService, EffectiveDataScope scope) {
        PluginRuntimeRecord runtime = runtimeStore.findRuntimeByInstallationId(installationId)
                .orElseThrow(() -> new ResourceNotFoundException("Plugin installation not found"));
        if (!dataScopeService.allows(scope, runtime.ownerUserId(), runtime.ownerDepartmentId())) {
            throw new ResourceNotFoundException("Plugin installation not found");
        }
        if (requireService && !runtimeStore.runtimeCapabilities(runtime.capabilitiesJson()).contains(PluginCapability.SERVICE)) {
            throw new IllegalArgumentException("Target plugin does not support service calls");
        }
        return runtime;
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
