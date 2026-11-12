package com.sky.job.plugin.management;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.plugin.management.dto.*;
import com.sky.job.plugin.runtime.PluginRuntimeRecord;
import com.sky.job.plugin.security.PluginCapability;
import com.sky.job.plugin.storage.*;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.job.security.*;
import com.sky.job.system.DynamicPluginStorageSettings;
import com.sky.job.web.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PluginAccountGroupServiceTest {
    private static final String INSTALLATION = "installation-a";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 12, 0);
    @Mock PluginRuntimeMetadataStore runtimes;
    @Mock PluginConfigStore configs;
    @Mock PluginAccountStore accounts;
    @Mock PluginAccountGroupStore groups;
    @Mock PluginAuditStore audit;
    @Mock DataScopeService scopes;
    @Mock PluginAccountListViewService views;
    private final ObjectMapper json = new ObjectMapper();
    private final AuthenticatedUser user = new AuthenticatedUser("user-1", "alice", "", 1L, true, Set.of("ADMIN"), Set.of());
    private PluginManagementService service;

    @BeforeEach
    void setUp() {
        PluginRuntimeRecord runtime = mock(PluginRuntimeRecord.class);
        lenient().when(runtime.ownerUserId()).thenReturn(user.userId());
        lenient().when(runtime.ownerDepartmentId()).thenReturn(1L);
        lenient().when(runtime.installationId()).thenReturn(INSTALLATION);
        lenient().when(runtime.pluginId()).thenReturn("demo");
        lenient().when(runtime.instanceId()).thenReturn("runner-a");
        lenient().when(runtime.capabilitiesJson()).thenReturn("{\"account\":true}");
        lenient().when(runtimes.findRuntimeByInstallationId(INSTALLATION)).thenReturn(Optional.of(runtime));
        lenient().when(runtimes.runtimeCapabilities(any())).thenReturn(Set.of(PluginCapability.ACCOUNT));
        EffectiveDataScope scope = EffectiveDataScope.all(user.userId());
        lenient().when(scopes.resolve(eq(user), anyString())).thenReturn(scope);
        lenient().when(scopes.allows(scope, user.userId(), 1L)).thenReturn(true);
        DynamicPluginStorageSettings settings = mock(DynamicPluginStorageSettings.class);
        lenient().when(settings.maxAccountJsonBytes()).thenReturn(1_000_000);
        service = new PluginManagementService(runtimes, configs, accounts, new PluginPayloadValidator(json, settings), audit, json, scopes, views, groups);
    }

    @Test
    void createsTrimmedGroupAndAuditsName() {
        when(groups.insert(INSTALLATION, "Primary")).thenReturn(10L);
        when(groups.find(INSTALLATION, 10L)).thenReturn(Optional.of(group()));
        assertThat(service.createAccountGroup(INSTALLATION, new UpsertPluginAccountGroupRequest(" Primary "), user).groupId()).isEqualTo(10);
        verify(audit).insert(eq(INSTALLATION), eq("demo"), eq("runner-a"), eq("alice"), eq("PLUGIN_ACCOUNT_GROUP_CREATE"), contains("Primary"));
    }

    @Test
    void duplicateNameIsConflictAndInvalidNamesDoNotWrite() {
        when(groups.insert(INSTALLATION, "Primary")).thenThrow(new DuplicateKeyException("duplicate"));
        assertThatThrownBy(() -> service.createAccountGroup(INSTALLATION, new UpsertPluginAccountGroupRequest("Primary"), user))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already exists");
        for (String name : List.of(" ", "x".repeat(129))) {
            assertThatThrownBy(() -> service.createAccountGroup(INSTALLATION, new UpsertPluginAccountGroupRequest(name), user))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(groups, times(1)).insert(anyString(), anyString());
    }

    @Test
    void renamesGroupAndUsesDatabaseUniqueness() {
        when(groups.lock(INSTALLATION, 10L)).thenReturn(Optional.of(group()));
        when(groups.find(INSTALLATION, 10L)).thenReturn(Optional.of(new PluginAccountGroup(10, "Backup", 0, NOW, NOW)));
        assertThat(service.renameAccountGroup(INSTALLATION, 10, new UpsertPluginAccountGroupRequest(" Backup "), user).name()).isEqualTo("Backup");
        when(groups.rename(INSTALLATION, 10L, "Taken")).thenThrow(new DuplicateKeyException("duplicate"));
        assertThatThrownBy(() -> service.renameAccountGroup(INSTALLATION, 10, new UpsertPluginAccountGroupRequest("Taken"), user))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deletingGroupClearsMembershipBeforeDeletingWithoutDeletingAccounts() {
        when(groups.lock(INSTALLATION, 10L)).thenReturn(Optional.of(group()));
        when(accounts.clearGroup(INSTALLATION, 10L)).thenReturn(3);
        service.deleteAccountGroup(INSTALLATION, 10, user);
        var order = inOrder(accounts, groups);
        order.verify(groups).lock(INSTALLATION, 10);
        order.verify(accounts).clearGroup(INSTALLATION, 10);
        order.verify(groups).delete(INSTALLATION, 10);
        verify(accounts, never()).delete(anyString(), anyString());
    }

    @Test
    void explicitNullClearsGroupButOmittedFieldIsRejected() throws Exception {
        when(accounts.getManaged(INSTALLATION, "a")).thenReturn(Optional.of(account("a", null)));
        var clear = json.readValue("{\"groupId\":null}", UpdatePluginAccountGroupRequest.class);
        assertThat(service.updateAccountGroup(INSTALLATION, "a", clear, user).groupId()).isNull();
        verify(accounts).updateGroup(INSTALLATION, "a", null);
        var omitted = json.readValue("{}", UpdatePluginAccountGroupRequest.class);
        assertThatThrownBy(() -> service.updateAccountGroup(INSTALLATION, "a", omitted, user))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("field is required");
        verifyNoInteractions(groups);
    }

    @Test
    void batchReportsMissingDuplicateAndInvalidIdsIndividually() throws Exception {
        when(groups.lock(INSTALLATION, 10L)).thenReturn(Optional.of(group()));
        when(accounts.getManaged(INSTALLATION, "a")).thenReturn(Optional.of(account("a", null)));
        when(accounts.updateGroup(INSTALLATION, "a", 10L)).thenReturn(1);
        when(accounts.getManaged(INSTALLATION, "missing")).thenReturn(Optional.empty());
        var request = json.readValue("{\"accountIds\":[\" a \",\"a\",\"missing\",\"\"],\"groupId\":10}", UpdatePluginAccountGroupRequest.class);
        var result = service.updateAccountsGroup(INSTALLATION, request, user);
        assertThat(result.total()).isEqualTo(4);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(3);
        assertThat(result.receipts()).extracting(BatchPluginAccountGroupReceipt::success).containsExactly(true, false, false, false);
        verify(accounts, times(1)).updateGroup(anyString(), anyString(), any());
    }

    @Test
    void batchRejectsForeignGroupBeforeAnyAccountWrite() throws Exception {
        when(groups.lock(INSTALLATION, 20L)).thenReturn(Optional.empty());
        var request = json.readValue("{\"accountIds\":[\"a\"],\"groupId\":20}", UpdatePluginAccountGroupRequest.class);
        assertThatThrownBy(() -> service.updateAccountsGroup(INSTALLATION, request, user)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(accounts);
    }

    @Test
    void groupFilterUsesSamePredicateForPageAndTotal() {
        when(groups.find(INSTALLATION, 10L)).thenReturn(Optional.of(group()));
        when(accounts.list(INSTALLATION, true, "a", "Name", 10L, false, 2, 5)).thenReturn(List.of());
        service.listAccounts(INSTALLATION, true, "a", "Name", 10L, false, user, 2, 5);
        verify(accounts).count(INSTALLATION, true, "a", "Name", 10L, false);
        verify(views).listItems(eq(INSTALLATION), eq(user.userId()), any(), eq(List.of()));
    }

    @Test
    void rejectsContradictoryFiltersAndForeignGroup() {
        assertThatThrownBy(() -> service.listAccounts(INSTALLATION, null, null, null, 10L, true, user, 1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        when(groups.find(INSTALLATION, 20L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.exportAccounts(INSTALLATION, null, null, null, 20L, false, "csv", false, user))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(accounts);
    }

    @Test
    void updateWithoutGroupDoesNotChangeMembershipAndReturnedDetailContainsGroup() {
        when(accounts.getManaged(INSTALLATION, "a")).thenReturn(Optional.of(account("a", 10L)));
        var result = service.updateAccount(INSTALLATION, "a", new UpsertPluginAccountRequest("a", "Name", "{}", true, null), user);
        assertThat(result.groupId()).isEqualTo(10);
        assertThat(result.groupName()).isEqualTo("Primary");
        verify(accounts, never()).updateGroup(anyString(), anyString(), any());
        verifyNoInteractions(groups);
    }

    @Test
    void creatingWithForeignGroupDoesNotUpsertAccount() {
        when(groups.lock(INSTALLATION, 20L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createAccount(INSTALLATION, new UpsertPluginAccountRequest("a", "Name", "{}", true, 20L), user))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(accounts);
    }

    @Test
    void rejectsOutOfScopeInstallationsAndUnsupportedCapability() {
        when(scopes.allows(any(), anyString(), any())).thenReturn(false);
        assertThatThrownBy(() -> service.listAccountGroups(INSTALLATION, user)).isInstanceOf(ResourceNotFoundException.class);
        when(scopes.allows(any(), anyString(), any())).thenReturn(true);
        when(runtimes.runtimeCapabilities(any())).thenReturn(Set.of(PluginCapability.CONFIG));
        assertThatThrownBy(() -> service.createAccountGroup(INSTALLATION, new UpsertPluginAccountGroupRequest("Primary"), user))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("account management");
        verifyNoInteractions(groups);
    }

    private PluginAccountGroup group() { return new PluginAccountGroup(10, "Primary", 0, NOW, NOW); }
    private PluginAccount account(String id, Long groupId) {
        return new PluginAccount(1, INSTALLATION, id, "Name", "{}", true, NOW, NOW, groupId, groupId == null ? null : "Primary");
    }
}
