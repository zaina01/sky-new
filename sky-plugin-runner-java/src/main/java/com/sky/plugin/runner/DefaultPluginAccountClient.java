package com.sky.plugin.runner;

import com.sky.plugin.api.PluginAccountClient;
import com.sky.plugin.protocol.AccountRequest;
import com.sky.plugin.protocol.AccountResponse;

import java.util.List;
import java.util.Optional;

class DefaultPluginAccountClient implements PluginAccountClient {
    private final String pluginId;
    private final HostGrpcClient hostClient;

    DefaultPluginAccountClient(String pluginId, HostGrpcClient hostClient) {
        this.pluginId = pluginId;
        this.hostClient = hostClient;
    }

    @Override
    public List<String> listJson() {
        return hostClient.listAccounts(request("", "")).getAccountsList().stream()
                .map(account -> account.getAccountJson().isBlank() ? "{}" : account.getAccountJson())
                .toList();
    }

    @Override
    public Optional<String> getJson(String accountId) {
        AccountResponse response = hostClient.getAccount(request(accountId, ""));
        return response.getAccountsList().stream()
                .findFirst()
                .map(account -> account.getAccountJson().isBlank() ? "{}" : account.getAccountJson());
    }

    @Override
    public <T> List<T> list(Class<T> type) {
        return listJson().stream().map(json -> PluginJsonMapper.read(json, type)).toList();
    }

    @Override
    public <T> Optional<T> get(String accountId, Class<T> type) {
        return getJson(accountId).map(json -> PluginJsonMapper.read(json, type));
    }

    @Override
    public <T> Optional<T> first(Class<T> type) {
        return list(type).stream().findFirst();
    }

    @Override
    public boolean update(Object account) {
        return hostClient.updateAccount(request(PluginJsonMapper.accountId(account), PluginJsonMapper.write(account))).getSuccess();
    }

    private AccountRequest request(String accountId, String accountJson) {
        return AccountRequest.newBuilder()
                .setPluginId(pluginId)
                .setAccountId(accountId == null ? "" : accountId)
                .setAccountJson(accountJson == null ? "" : accountJson)
                .build();
    }
}
