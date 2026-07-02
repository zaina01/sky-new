package com.sky.plugin.api;

import java.util.List;
import java.util.Optional;

public interface PluginAccountClient {
    List<String> listJson();

    Optional<String> getJson(String accountId);

    <T> List<T> list(Class<T> type);

    <T> Optional<T> get(String accountId, Class<T> type);

    <T> Optional<T> first(Class<T> type);

    boolean update(Object account);
}
