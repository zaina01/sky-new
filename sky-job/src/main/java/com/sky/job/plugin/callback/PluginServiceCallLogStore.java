package com.sky.job.plugin.callback;

import com.sky.job.plugin.callback.mapper.PluginServiceCallLogMapper;
import com.sky.job.security.EffectiveDataScope;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PluginServiceCallLogStore {
    private final PluginServiceCallLogMapper mapper;

    public PluginServiceCallLogStore(PluginServiceCallLogMapper mapper) {
        this.mapper = mapper;
    }

    public void insert(PluginServiceCallLogCommand command) {
        mapper.insert(command);
    }

    public Optional<PluginServiceCallLog> findById(long id, EffectiveDataScope scope) {
        return Optional.ofNullable(mapper.findById(id, scope));
    }

    public List<PluginServiceCallLog> list(Long jobId, String callerInstallationId, String callerPluginId, String targetInstallationId, String targetPluginId, String operation, Boolean success, EffectiveDataScope scope, Integer page, Integer size) {
        int normalizedPage = normalizedPage(page);
        int normalizedSize = normalizedSize(size);
        return mapper.list(jobId, trim(callerInstallationId), trim(callerPluginId), trim(targetInstallationId), trim(targetPluginId), trim(operation), success, scope, normalizedSize, (normalizedPage - 1) * normalizedSize);
    }

    public long count(Long jobId, String callerInstallationId, String callerPluginId, String targetInstallationId, String targetPluginId, String operation, Boolean success, EffectiveDataScope scope) {
        return mapper.count(jobId, trim(callerInstallationId), trim(callerPluginId), trim(targetInstallationId), trim(targetPluginId), trim(operation), success, scope);
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int normalizedPage(Integer page) {
        return Math.max(page == null ? 1 : page, 1);
    }

    private int normalizedSize(Integer size) {
        return Math.min(Math.max(size == null ? 20 : size, 1), 100);
    }
}
