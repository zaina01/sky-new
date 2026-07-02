package com.sky.job.system;

import org.springframework.stereotype.Component;

@Component
public class DynamicPluginArtifactSettings {
    private final SystemSettingProvider provider;

    public DynamicPluginArtifactSettings(SystemSettingProvider provider) {
        this.provider = provider;
    }

    public long maxArtifactBytes() {
        return provider.getLong(SystemSettingDefinitions.PLUGIN_UPLOAD_MAX_ARTIFACT_BYTES);
    }
}
