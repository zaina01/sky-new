package com.sky.job.plugin.storage;

import com.sky.job.plugin.storage.mapper.PluginConfigMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PluginConfigStore {
    private final PluginConfigMapper mapper;

    public PluginConfigStore(PluginConfigMapper mapper) {
        this.mapper = mapper;
    }

    public String getConfigJson(String installationId) {
        String configJson = mapper.findConfigJson(installationId);
        return configJson == null ? "{}" : configJson;
    }

    public void upsertConfigJson(String installationId, String configJson) {
        mapper.upsertConfigJson(installationId, configJson);
    }

    public int deleteByInstallationId(String installationId) {
        return mapper.deleteByInstallationId(installationId);
    }
}
