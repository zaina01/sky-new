package com.sky.job.plugin.callback;

import com.sky.job.plugin.callback.mapper.PluginServicePermissionMapper;
import com.sky.job.security.EffectiveDataScope;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PluginServicePermissionStore {
    private final PluginServicePermissionMapper mapper;

    public PluginServicePermissionStore(PluginServicePermissionMapper mapper) {
        this.mapper = mapper;
    }

    public boolean isAllowed(String callerInstallationId, String targetInstallationId, String operation) {
        return mapper.countEnabled(callerInstallationId, targetInstallationId, operation) > 0;
    }

    public List<String> authorizedTargetInstallationIds(String callerInstallationId, String targetPluginId, String operation) {
        return mapper.authorizedTargetInstallationIds(callerInstallationId, targetPluginId, operation);
    }

    public List<PluginServicePermission> list(String callerInstallationId, String callerPluginId, String targetInstallationId, String targetPluginId, String operation, Boolean enabled, EffectiveDataScope scope, Integer page, Integer size) {
        int normalizedPage = Math.max(page == null ? 1 : page, 1);
        int normalizedSize = Math.min(Math.max(size == null ? 20 : size, 1), 100);
        return mapper.list(trim(callerInstallationId), trim(callerPluginId), trim(targetInstallationId), trim(targetPluginId), trim(operation), enabled, scope, normalizedSize, (normalizedPage - 1) * normalizedSize);
    }

    public long count(String callerInstallationId, String callerPluginId, String targetInstallationId, String targetPluginId, String operation, Boolean enabled, EffectiveDataScope scope) {
        return mapper.count(trim(callerInstallationId), trim(callerPluginId), trim(targetInstallationId), trim(targetPluginId), trim(operation), enabled, scope);
    }

    public Optional<PluginServicePermission> findById(long id, EffectiveDataScope scope) {
        return Optional.ofNullable(mapper.findById(id, scope));
    }

    public void upsert(String callerInstallationId, String targetInstallationId, String operation, boolean enabled) {
        mapper.upsert(callerInstallationId, targetInstallationId, operation, enabled);
    }

    public int updateEnabled(long id, boolean enabled) {
        return mapper.updateEnabled(id, enabled);
    }

    public int delete(long id) {
        return mapper.delete(id);
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
