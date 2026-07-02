package com.sky.job.plugin.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sky.plugin.storage")
public record PluginStorageProperties(
        int maxDataJsonBytes,
        int maxConfigJsonBytes,
        int maxAccountJsonBytes,
        int maxLogMessageBytes,
        int selectAllLimit,
        int maxDataRowsPerPlugin
) {
    public PluginStorageProperties {
        if (maxDataJsonBytes <= 0) maxDataJsonBytes = 65536;
        if (maxConfigJsonBytes <= 0) maxConfigJsonBytes = 65536;
        if (maxAccountJsonBytes <= 0) maxAccountJsonBytes = 65536;
        if (maxLogMessageBytes <= 0) maxLogMessageBytes = 8192;
        if (selectAllLimit <= 0) selectAllLimit = 100;
        if (maxDataRowsPerPlugin <= 0) maxDataRowsPerPlugin = 10000;
    }
}
