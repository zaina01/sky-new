package com.sky.plugin.runner;

import com.sky.plugin.api.PluginConfigClient;
import com.sky.plugin.protocol.ConfigRequest;
import com.sky.plugin.protocol.ConfigResponse;

class DefaultPluginConfigClient implements PluginConfigClient {
    private final String pluginId;
    private final HostGrpcClient hostClient;

    DefaultPluginConfigClient(String pluginId, HostGrpcClient hostClient) {
        this.pluginId = pluginId;
        this.hostClient = hostClient;
    }

    @Override
    public String getConfigJson() {
        ConfigResponse response = hostClient.getConfig(request(""));
        return response.getConfigJson();
    }

    @Override
    public <T> T get(Class<T> type) {
        return PluginJsonMapper.read(getConfigJson(), type);
    }

    @Override
    public boolean update(Object config) {
        return hostClient.updateConfig(request(PluginJsonMapper.write(config))).getSuccess();
    }

    private ConfigRequest request(String configJson) {
        return ConfigRequest.newBuilder()
                .setPluginId(pluginId)
                .setConfigJson(configJson == null ? "" : configJson)
                .build();
    }
}
