package com.sky.job.it.plugin;

import com.sky.plugin.api.ConfigField;
import com.sky.plugin.api.ConfigFieldType;
import com.sky.plugin.api.PluginConfig;

@PluginConfig("Frontend Integration Config")
public class FrontendIntegrationConfig {
    @ConfigField(label = "Message", type = ConfigFieldType.TEXT)
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
