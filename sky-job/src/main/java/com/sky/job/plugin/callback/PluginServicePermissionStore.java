package com.sky.job.plugin.callback;

import com.sky.job.plugin.callback.mapper.PluginServicePermissionMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PluginServicePermissionStore {
    private final PluginServicePermissionMapper mapper;

    public PluginServicePermissionStore(PluginServicePermissionMapper mapper) {
        this.mapper = mapper;
    }

    public boolean isAllowed(String callerPluginId, String targetPluginId, String operation) {
        return mapper.countEnabled(callerPluginId, targetPluginId, operation) > 0;
    }

    public List<PluginServicePermission> list(String callerPluginId, String targetPluginId, String operation, Boolean enabled, Integer page, Integer size) {
        int normalizedPage = Math.max(page == null ? 1 : page, 1);
        int normalizedSize = Math.min(Math.max(size == null ? 20 : size, 1), 100);
        return mapper.list(trim(callerPluginId), trim(targetPluginId), trim(operation), enabled, normalizedSize, (normalizedPage - 1) * normalizedSize);
    }

    public long count(String callerPluginId, String targetPluginId, String operation, Boolean enabled) {
        return mapper.count(trim(callerPluginId), trim(targetPluginId), trim(operation), enabled);
    }

    public Optional<PluginServicePermission> findById(long id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    public void upsert(String callerPluginId, String targetPluginId, String operation, boolean enabled) {
        mapper.upsert(callerPluginId, targetPluginId, operation, enabled);
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
