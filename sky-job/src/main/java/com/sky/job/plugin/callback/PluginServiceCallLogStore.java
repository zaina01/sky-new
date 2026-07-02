package com.sky.job.plugin.callback;

import com.sky.job.plugin.callback.mapper.PluginServiceCallLogMapper;
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

    public Optional<PluginServiceCallLog> findById(long id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    public List<PluginServiceCallLog> list(Long jobId, String callerPluginId, String targetPluginId, String operation, Boolean success, Integer page, Integer size) {
        int normalizedPage = normalizedPage(page);
        int normalizedSize = normalizedSize(size);
        return mapper.list(jobId, trim(callerPluginId), trim(targetPluginId), trim(operation), success, normalizedSize, (normalizedPage - 1) * normalizedSize);
    }

    public long count(Long jobId, String callerPluginId, String targetPluginId, String operation, Boolean success) {
        return mapper.count(jobId, trim(callerPluginId), trim(targetPluginId), trim(operation), success);
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
