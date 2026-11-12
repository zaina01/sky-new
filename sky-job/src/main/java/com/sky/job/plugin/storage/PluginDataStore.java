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

    public void upsert(String installationId, String collection, String dataKey, String dataJson) {
        if (!exists(installationId, collection, dataKey) && countRows(installationId) >= properties.maxDataRowsPerPlugin()) {
            throw new IllegalStateException("Plugin data row quota exceeded");
        }
        mapper.upsert(installationId, collection, dataKey, dataJson);
    }

    public boolean delete(String installationId, String collection, String dataKey) {
        return mapper.delete(installationId, collection, dataKey) > 0;
    }

    public Optional<String> select(String installationId, String collection, String dataKey) {
        return Optional.ofNullable(mapper.select(installationId, collection, dataKey));
    }

    public int deleteByInstallationId(String installationId) {
        return mapper.deleteByInstallationId(installationId);
    }

    public String selectAll(String installationId, String collection) {
        String value = mapper.selectAll(installationId, collection, properties.selectAllLimit());
        return value == null ? "[]" : value;
    }

    private boolean exists(String installationId, String collection, String dataKey) {
        return mapper.countByKey(installationId, collection, dataKey) > 0;
    }

    private int countRows(String installationId) {
        return mapper.countRows(installationId);
    }
}
