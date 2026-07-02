package com.sky.job.plugin.storage;

import com.sky.job.plugin.storage.mapper.PluginAccountMapper;
import com.sky.plugin.protocol.AccountRecord;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PluginAccountStore {
    private final PluginAccountMapper mapper;

    public PluginAccountStore(PluginAccountMapper mapper) {
        this.mapper = mapper;
    }

    public List<AccountRecord> list(String pluginId) {
        return mapper.listByPluginId(pluginId).stream().map(this::toRecord).toList();
    }

    public List<AccountRecord> listEnabled(String pluginId) {
        return mapper.listEnabledByPluginId(pluginId).stream().map(this::toRecord).toList();
    }

    public Optional<AccountRecord> get(String pluginId, String accountId) {
        return Optional.ofNullable(mapper.getRecord(pluginId, accountId)).map(this::toRecord);
    }

    public Optional<AccountRecord> getEnabled(String pluginId, String accountId) {
        return Optional.ofNullable(mapper.getEnabledRecord(pluginId, accountId)).map(this::toRecord);
    }

    public List<PluginAccount> list(String pluginId, Boolean enabled, String accountId, String displayName, Integer page, Integer size) {
        int normalizedPage = Math.max(page == null ? 1 : page, 1);
        int normalizedSize = Math.min(Math.max(size == null ? 20 : size, 1), 100);
        return mapper.listManaged(pluginId, enabled, accountId, displayName, like(displayName), normalizedSize, (normalizedPage - 1) * normalizedSize);
    }

    public long count(String pluginId, Boolean enabled, String accountId, String displayName) {
        return mapper.countManaged(pluginId, enabled, accountId, displayName, like(displayName));
    }

    public List<PluginAccount> listManagedForExport(String pluginId, Boolean enabled, String accountId, String displayName, int limit) {
        return mapper.listManagedForExport(pluginId, enabled, accountId, displayName, like(displayName), limit);
    }

    public Optional<PluginAccount> getManaged(String pluginId, String accountId) {
        return Optional.ofNullable(mapper.getRecord(pluginId, accountId));
    }

    public void upsert(String pluginId, String accountId, String accountJson) {
        upsert(pluginId, accountId, accountId, accountJson, true);
    }

    public void upsert(String pluginId, String accountId, String displayName, String accountJson, boolean enabled) {
        mapper.upsert(pluginId, accountId, displayName, accountJson, enabled);
    }

    public int updateEnabled(String pluginId, String accountId, boolean enabled) {
        return mapper.updateEnabled(pluginId, accountId, enabled);
    }

    public int delete(String pluginId, String accountId) {
        return mapper.delete(pluginId, accountId);
    }

    public int deleteByPluginId(String pluginId) {
        return mapper.deleteByPluginId(pluginId);
    }

    private String like(String value) {
        return value == null || value.isBlank() ? null : "%" + value + "%";
    }

    private AccountRecord toRecord(PluginAccount account) {
        return AccountRecord.newBuilder()
                .setAccountId(account.accountId())
                .setDisplayName(account.displayName() == null ? "" : account.displayName())
                .setAccountJson(account.accountJson() == null ? "{}" : account.accountJson())
                .setEnabled(account.enabled())
                .build();
    }
}
