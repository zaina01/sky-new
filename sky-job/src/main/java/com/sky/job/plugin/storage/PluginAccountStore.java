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

    public List<AccountRecord> list(String installationId) {
        return mapper.listByInstallationId(installationId).stream().map(this::toRecord).toList();
    }

    public List<AccountRecord> listEnabled(String installationId) {
        return mapper.listEnabledByInstallationId(installationId).stream().map(this::toRecord).toList();
    }

    public Optional<AccountRecord> get(String installationId, String accountId) {
        return Optional.ofNullable(mapper.getRecord(installationId, accountId)).map(this::toRecord);
    }

    public Optional<AccountRecord> getEnabled(String installationId, String accountId) {
        return Optional.ofNullable(mapper.getEnabledRecord(installationId, accountId)).map(this::toRecord);
    }

    public List<PluginAccount> list(String installationId, Boolean enabled, String accountId, String displayName, Integer page, Integer size) {
        return list(installationId, enabled, accountId, displayName, null, false, page, size);
    }

    public List<PluginAccount> list(String installationId, Boolean enabled, String accountId, String displayName, Long groupId, boolean ungrouped, Integer page, Integer size) {
        int normalizedPage = Math.max(page == null ? 1 : page, 1);
        int normalizedSize = Math.min(Math.max(size == null ? 20 : size, 1), 100);
        return mapper.listManaged(installationId, enabled, accountId, like(accountId), displayName, like(displayName), groupId, ungrouped, normalizedSize, (normalizedPage - 1) * normalizedSize);
    }

    public long count(String installationId, Boolean enabled, String accountId, String displayName) {
        return count(installationId, enabled, accountId, displayName, null, false);
    }

    public long count(String installationId, Boolean enabled, String accountId, String displayName, Long groupId, boolean ungrouped) {
        return mapper.countManaged(installationId, enabled, accountId, like(accountId), displayName, like(displayName), groupId, ungrouped);
    }

    public List<PluginAccount> listManagedForExport(String installationId, Boolean enabled, String accountId, String displayName, int limit) {
        return listManagedForExport(installationId, enabled, accountId, displayName, null, false, limit);
    }

    public List<PluginAccount> listManagedForExport(String installationId, Boolean enabled, String accountId, String displayName, Long groupId, boolean ungrouped, int limit) {
        return mapper.listManagedForExport(installationId, enabled, accountId, like(accountId), displayName, like(displayName), groupId, ungrouped, limit);
    }

    public Optional<PluginAccount> getManaged(String installationId, String accountId) {
        return Optional.ofNullable(mapper.getRecord(installationId, accountId));
    }

    public void upsert(String installationId, String accountId, String accountJson) {
        upsert(installationId, accountId, accountId, accountJson, true);
    }

    public void upsert(String installationId, String accountId, String displayName, String accountJson, boolean enabled) {
        mapper.upsert(installationId, accountId, displayName, accountJson, enabled);
    }

    public int updateEnabled(String installationId, String accountId, boolean enabled) {
        return mapper.updateEnabled(installationId, accountId, enabled);
    }

    public int delete(String installationId, String accountId) {
        return mapper.delete(installationId, accountId);
    }

    public int deleteByInstallationId(String installationId) {
        return mapper.deleteByInstallationId(installationId);
    }

    public int updateGroup(String installationId, String accountId, Long groupId) {
        return mapper.updateGroup(installationId, accountId, groupId);
    }

    public int clearGroup(String installationId, long groupId) {
        return mapper.clearGroup(installationId, groupId);
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
