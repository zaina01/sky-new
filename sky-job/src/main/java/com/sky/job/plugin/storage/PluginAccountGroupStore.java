package com.sky.job.plugin.storage;

import com.sky.job.plugin.storage.mapper.PluginAccountGroupMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PluginAccountGroupStore {
    private final PluginAccountGroupMapper mapper;

    public PluginAccountGroupStore(PluginAccountGroupMapper mapper) {
        this.mapper = mapper;
    }

    public List<PluginAccountGroup> list(String installationId) {
        return mapper.list(installationId);
    }

    public Optional<PluginAccountGroup> find(String installationId, long groupId) {
        return Optional.ofNullable(mapper.find(installationId, groupId));
    }

    public Optional<PluginAccountGroup> lock(String installationId, long groupId) {
        return Optional.ofNullable(mapper.lock(installationId, groupId));
    }

    public long insert(String installationId, String name) {
        Map<String, Object> values = new HashMap<>();
        values.put("installationId", installationId);
        values.put("name", name);
        mapper.insert(values);
        if (values.get("groupId") instanceof Number id) return id.longValue();
        throw new IllegalStateException("Plugin account group was not saved");
    }

    public int rename(String installationId, long groupId, String name) {
        return mapper.rename(installationId, groupId, name);
    }

    public int delete(String installationId, long groupId) {
        return mapper.delete(installationId, groupId);
    }

    public int deleteByInstallationId(String installationId) {
        return mapper.deleteByInstallationId(installationId);
    }
}
