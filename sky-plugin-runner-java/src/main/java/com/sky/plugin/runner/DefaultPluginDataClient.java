package com.sky.plugin.runner;

import com.sky.plugin.api.PluginDataClient;
import com.sky.plugin.api.PluginDataRecord;
import com.sky.plugin.protocol.PluginDataRequest;
import com.sky.plugin.protocol.PluginDataResponse;

import java.util.List;
import java.util.Optional;

class DefaultPluginDataClient implements PluginDataClient {
    private final String pluginId;
    private final HostGrpcClient hostClient;

    DefaultPluginDataClient(String pluginId, HostGrpcClient hostClient) {
        this.pluginId = pluginId;
        this.hostClient = hostClient;
    }

    @Override
    public Optional<String> getJson(String collection, String key) {
        PluginDataResponse response = hostClient.getData(request(collection, key, ""));
        return response.getSuccess() ? Optional.of(response.getDataJson().isBlank() ? "{}" : response.getDataJson()) : Optional.empty();
    }

    @Override
    public <T> Optional<T> get(String collection, String key, Class<T> type) {
        return getJson(collection, key).map(json -> PluginJsonMapper.read(json, type));
    }

    @Override
    public List<PluginDataRecord> listJson(String collection) {
        return hostClient.listData(request(collection, "", "")).getRecordsList().stream()
                .map(record -> new PluginDataRecord(record.getKey(), record.getDataJson().isBlank() ? "{}" : record.getDataJson()))
                .toList();
    }

    @Override
    public <T> List<T> list(String collection, Class<T> type) {
        return listJson(collection).stream()
                .map(record -> PluginJsonMapper.read(record.json(), type))
                .toList();
    }

    @Override
    public boolean putJson(String collection, String key, String dataJson) {
        return hostClient.putData(request(collection, key, dataJson)).getSuccess();
    }

    @Override
    public boolean put(String collection, String key, Object data) {
        return putJson(collection, key, PluginJsonMapper.write(data));
    }

    @Override
    public boolean delete(String collection, String key) {
        return hostClient.deleteData(request(collection, key, "")).getDeleted();
    }

    private PluginDataRequest request(String collection, String key, String dataJson) {
        return PluginDataRequest.newBuilder()
                .setPluginId(pluginId)
                .setCollection(collection == null ? "" : collection)
                .setKey(key == null ? "" : key)
                .setDataJson(dataJson == null ? "" : dataJson)
                .build();
    }
}
