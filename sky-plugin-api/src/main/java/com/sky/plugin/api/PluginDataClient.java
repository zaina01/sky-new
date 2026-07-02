package com.sky.plugin.api;

import java.util.List;
import java.util.Optional;

public interface PluginDataClient {
    Optional<String> getJson(String collection, String key);

    <T> Optional<T> get(String collection, String key, Class<T> type);

    List<PluginDataRecord> listJson(String collection);

    <T> List<T> list(String collection, Class<T> type);

    boolean putJson(String collection, String key, String dataJson);

    boolean put(String collection, String key, Object data);

    boolean delete(String collection, String key);
}
