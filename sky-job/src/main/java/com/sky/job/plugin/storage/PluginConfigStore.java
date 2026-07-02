package com.sky.job.plugin.storage;

import com.sky.job.plugin.storage.mapper.PluginConfigMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PluginConfigStore {
    private final PluginConfigMapper mapper;

    public PluginConfigStore(PluginConfigMapper mapper) {
        this.mapper = mapper;
    }

    public String getConfigJson(String pluginId) {
        String configJson = mapper.findConfigJson(pluginId);
        return configJson == null ? "{}" : configJson;
    }

    public void upsertConfigJson(String pluginId, String configJson) {
        mapper.upsertConfigJson(pluginId, configJson);
    }

    public int deleteByPluginId(String pluginId) {
        return mapper.deleteByPluginId(pluginId);
    }
}
