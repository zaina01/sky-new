package com.sky.job.plugin.management;

import com.sky.job.plugin.management.dto.BatchDeletePluginAccountRequest;
import com.sky.job.plugin.management.dto.BatchDeletePluginAccountResponse;
import com.sky.job.plugin.management.dto.BatchUpdatePluginAccountStateRequest;
import com.sky.job.plugin.management.dto.BatchUpdatePluginAccountStateResponse;
import com.sky.job.plugin.management.dto.PluginAccountExportFile;
import com.sky.job.plugin.management.dto.PluginAccountImportResponse;
import com.sky.job.plugin.management.dto.PluginAccountListItemResponse;
import com.sky.job.plugin.management.dto.PluginAccountResponse;
import com.sky.job.plugin.management.dto.PluginAccountSchemaResponse;
import com.sky.job.plugin.management.dto.PluginAccountViewPreferenceResponse;
import com.sky.job.plugin.management.dto.PluginConfigResponse;
import com.sky.job.plugin.management.dto.UpdatePluginAccountStateRequest;
import com.sky.job.plugin.management.dto.UpdatePluginAccountViewPreferenceRequest;
import com.sky.job.plugin.management.dto.UpdatePluginConfigRequest;
import com.sky.job.plugin.management.dto.UpsertPluginAccountRequest;
import com.sky.job.plugin.management.dto.UpsertPluginAccountGroupRequest;
import com.sky.job.plugin.management.dto.UpdatePluginAccountGroupRequest;
import com.sky.job.plugin.management.dto.BatchUpdatePluginAccountGroupResponse;
import com.sky.job.plugin.storage.PluginAccountGroup;
import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.security.AuthenticatedUser;
import com.sky.job.security.PermissionNames;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/plugins/installations/{installationId}")
public class PluginManagementController {
    private final PluginManagementService managementService;

    public PluginManagementController(PluginManagementService managementService) {
        this.managementService = managementService;
    }

    @GetMapping("/config")
    public PluginConfigResponse getConfig(@PathVariable String installationId, @AuthenticationPrincipal AuthenticatedUser user) {
        return managementService.getConfig(installationId, user);
    }

    @PutMapping("/config")
    public PluginConfigResponse updateConfig(
            @PathVariable String installationId,
            @RequestBody(required = false) UpdatePluginConfigRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return managementService.updateConfig(installationId, request, user);
    }

    @GetMapping("/accounts")
    public PageResponse<PluginAccountListItemResponse> listAccounts(
            @PathVariable String installationId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String displayName,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Boolean ungrouped,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return managementService.listAccounts(installationId, enabled, accountId, displayName, groupId, ungrouped, user, page, size);
    }

    @GetMapping("/accounts/export")
    public ResponseEntity<byte[]> exportAccounts(
            @PathVariable String installationId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String displayName,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Boolean ungrouped,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) Boolean includeSecret,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        PluginAccountExportFile file = managementService.exportAccounts(installationId, enabled, accountId, displayName, groupId, ungrouped, format, includeSecret, user);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(file.filename()))
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @PostMapping("/accounts/import")
    public PluginAccountImportResponse importAccounts(
            @PathVariable String installationId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Boolean dryRun,
            @RequestParam(required = false) Boolean enabledDefault,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return managementService.importAccounts(installationId, file, dryRun, enabledDefault, user);
    }

    @GetMapping("/accounts/schema")
    public PluginAccountSchemaResponse getAccountSchema(@PathVariable String installationId, @AuthenticationPrincipal AuthenticatedUser user) {
        return managementService.getAccountSchema(installationId, user);
    }

    @GetMapping("/accounts/view-preference")
    public PluginAccountViewPreferenceResponse getAccountViewPreference(
            @PathVariable String installationId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return managementService.getAccountViewPreference(installationId, user);
    }

    @PutMapping("/accounts/view-preference")
    public PluginAccountViewPreferenceResponse updateAccountViewPreference(
            @PathVariable String installationId,
            @RequestBody(required = false) UpdatePluginAccountViewPreferenceRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return managementService.updateAccountViewPreference(installationId, request, user);
    }

    @PatchMapping("/accounts/enabled")
    public BatchUpdatePluginAccountStateResponse updateAccountsEnabled(
            @PathVariable String installationId,
            @RequestBody BatchUpdatePluginAccountStateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return managementService.updateAccountsEnabled(installationId, request, user);
    }

    @DeleteMapping("/accounts")
    public BatchDeletePluginAccountResponse deleteAccounts(
            @PathVariable String installationId,
            @RequestBody(required = false) BatchDeletePluginAccountRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return managementService.deleteAccounts(installationId, request, user);
    }

    @GetMapping("/accounts/{accountId}")
    public PluginAccountResponse getAccount(@PathVariable String installationId, @PathVariable String accountId, @AuthenticationPrincipal AuthenticatedUser user) {
        return managementService.getAccount(installationId, accountId, user, PermissionNames.PLUGIN_ACCOUNT_READ);
    }

    @PostMapping("/accounts")
    public PluginAccountResponse createAccount(
            @PathVariable String installationId,
            @RequestBody UpsertPluginAccountRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return managementService.createAccount(installationId, request, user);
    }

    @PutMapping("/accounts/{accountId}")
    public PluginAccountResponse updateAccount(
            @PathVariable String installationId,
            @PathVariable String accountId,
            @RequestBody(required = false) UpsertPluginAccountRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return managementService.updateAccount(installationId, accountId, request, user);
    }

    @PatchMapping("/accounts/{accountId}/enabled")
    public PluginAccountResponse updateAccountEnabled(
            @PathVariable String installationId,
            @PathVariable String accountId,
            @RequestBody UpdatePluginAccountStateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return managementService.updateAccountEnabled(installationId, accountId, request, user);
    }

    @DeleteMapping("/accounts/{accountId}")
    public Map<String, Boolean> deleteAccount(
            @PathVariable String installationId,
            @PathVariable String accountId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        managementService.deleteAccount(installationId, accountId, user);
        return Map.of("success", true);
    }

    @GetMapping("/account-groups")
    public List<PluginAccountGroup> listAccountGroups(@PathVariable String installationId, @AuthenticationPrincipal AuthenticatedUser user) {
        return managementService.listAccountGroups(installationId, user);
    }

    @PostMapping("/account-groups")
    public PluginAccountGroup createAccountGroup(@PathVariable String installationId, @RequestBody UpsertPluginAccountGroupRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return managementService.createAccountGroup(installationId, request, user);
    }

    @PutMapping("/account-groups/{groupId}")
    public PluginAccountGroup renameAccountGroup(@PathVariable String installationId, @PathVariable long groupId, @RequestBody UpsertPluginAccountGroupRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return managementService.renameAccountGroup(installationId, groupId, request, user);
    }

    @DeleteMapping("/account-groups/{groupId}")
    public Map<String, Boolean> deleteAccountGroup(@PathVariable String installationId, @PathVariable long groupId, @AuthenticationPrincipal AuthenticatedUser user) {
        managementService.deleteAccountGroup(installationId, groupId, user);
        return Map.of("success", true);
    }

    @PatchMapping("/accounts/{accountId}/group")
    public PluginAccountResponse updateAccountGroup(@PathVariable String installationId, @PathVariable String accountId, @RequestBody UpdatePluginAccountGroupRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return managementService.updateAccountGroup(installationId, accountId, request, user);
    }

    @PatchMapping("/accounts/group")
    public BatchUpdatePluginAccountGroupResponse updateAccountsGroup(@PathVariable String installationId, @RequestBody UpdatePluginAccountGroupRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return managementService.updateAccountsGroup(installationId, request, user);
    }

    private String contentDisposition(String filename) {
        String encoded = java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded;
    }
}
