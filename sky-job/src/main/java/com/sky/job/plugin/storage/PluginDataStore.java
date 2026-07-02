package com.sky.job.plugin.storage;

import com.sky.job.plugin.storage.mapper.PluginDataMapper;
import com.sky.job.system.DynamicPluginStorageSettings;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PluginDataStore {
    private final PluginDataMapper mapper;
    private final DynamicPluginStorageSettings properties;

    public PluginDataStore(PluginDataMapper mapper, DynamicPluginStorageSettings properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    public void upsert(String pluginId, String collection, String dataKey, String dataJson) {
        if (!exists(pluginId, collection, dataKey) && countRows(pluginId) >= properties.maxDataRowsPerPlugin()) {
            throw new IllegalStateException("Plugin data row quota exceeded");
        }
        mapper.upsert(pluginId, collection, dataKey, dataJson);
    }

    public boolean delete(String pluginId, String collection, String dataKey) {
        return mapper.delete(pluginId, collection, dataKey) > 0;
    }

    public Optional<String> select(String pluginId, String collection, String dataKey) {
        return Optional.ofNullable(mapper.select(pluginId, collection, dataKey));
    }

    public int deleteByPluginId(String pluginId) {
        return mapper.deleteByPluginId(pluginId);
    }

    public String selectAll(String pluginId, String collection) {
        String value = mapper.selectAll(pluginId, collection, properties.selectAllLimit());
        return value == null ? "[]" : value;
    }

    private boolean exists(String pluginId, String collection, String dataKey) {
        return mapper.countByKey(pluginId, collection, dataKey) > 0;
    }

    private int countRows(String pluginId) {
        return mapper.countRows(pluginId);
    }
}
