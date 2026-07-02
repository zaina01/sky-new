package com.sky.job.system;

import org.springframework.stereotype.Component;

@Component
public class DynamicPluginStorageSettings {
    private final SystemSettingProvider provider;

    public DynamicPluginStorageSettings(SystemSettingProvider provider) {
        this.provider = provider;
    }

    public int maxDataJsonBytes() {
        return provider.getInt(SystemSettingDefinitions.PLUGIN_STORAGE_MAX_DATA_JSON_BYTES);
    }

    public int maxConfigJsonBytes() {
        return provider.getInt(SystemSettingDefinitions.PLUGIN_STORAGE_MAX_CONFIG_JSON_BYTES);
    }

    public int maxAccountJsonBytes() {
        return provider.getInt(SystemSettingDefinitions.PLUGIN_STORAGE_MAX_ACCOUNT_JSON_BYTES);
    }

    public int maxLogMessageBytes() {
        return provider.getInt(SystemSettingDefinitions.PLUGIN_STORAGE_MAX_LOG_MESSAGE_BYTES);
    }

    public int selectAllLimit() {
        return provider.getInt(SystemSettingDefinitions.PLUGIN_STORAGE_SELECT_ALL_LIMIT);
    }

    public int maxDataRowsPerPlugin() {
        return provider.getInt(SystemSettingDefinitions.PLUGIN_STORAGE_MAX_DATA_ROWS_PER_PLUGIN);
    }
}
