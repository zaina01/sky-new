package com.sky.plugin.api;

public interface PluginConfigClient {
    String getConfigJson();

    <T> T get(Class<T> type);

    boolean update(Object config);
}
