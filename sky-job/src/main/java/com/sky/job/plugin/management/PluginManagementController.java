package com.sky.job.plugin.management;

import com.sky.job.plugin.management.dto.BatchUpdatePluginAccountStateRequest;
import com.sky.job.plugin.management.dto.BatchUpdatePluginAccountStateResponse;
import com.sky.job.plugin.management.dto.PluginAccountExportFile;
import com.sky.job.plugin.management.dto.PluginAccountImportResponse;
import com.sky.job.plugin.management.dto.PluginAccountResponse;
import com.sky.job.plugin.management.dto.PluginAccountSchemaResponse;
import com.sky.job.plugin.management.dto.PluginConfigResponse;
import com.sky.job.plugin.management.dto.UpdatePluginAccountStateRequest;
import com.sky.job.plugin.management.dto.UpdatePluginConfigRequest;
import com.sky.job.plugin.management.dto.UpsertPluginAccountRequest;
import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.security.AuthenticatedUser;
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

@RestController
@RequestMapping("/plugins/{pluginId}")
public class PluginManagementController {
    private final PluginManagementService managementService;

    public PluginManagementController(PluginManagementService managementService) {
        this.managementService = managementService;
    }

    @GetMapping("/config")
    public PluginConfigResponse getConfig(@PathVariable String pluginId) {
        return managementService.getConfig(pluginId);
    }

    @PutMapping("/config")
    public PluginConfigResponse updateConfig(
            @PathVariable String pluginId,
            @RequestBody(required = false) UpdatePluginConfigRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return managementService.updateConfig(pluginId, request, user.username());
    }

    @GetMapping("/accounts")
    public PageResponse<PluginAccountResponse> listAccounts(
            @PathVariable String pluginId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String displayName,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return managementService.listAccounts(pluginId, enabled, accountId, displayName, page, size);
    }

    @GetMapping("/accounts/export")
    public ResponseEntity<byte[]> exportAccounts(
            @PathVariable String pluginId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String displayName,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) Boolean includeSecret,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        PluginAccountExportFile file = managementService.exportAccounts(pluginId, enabled, accountId, displayName, format, includeSecret, user.username());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(file.filename()))
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    @PostMapping("/accounts/import")
    public PluginAccountImportResponse importAccounts(
            @PathVariable String pluginId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Boolean dryRun,
            @RequestParam(required = false) Boolean enabledDefault,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return managementService.importAccounts(pluginId, file, dryRun, enabledDefault, user.username());
    }

    @GetMapping("/accounts/schema")
    public PluginAccountSchemaResponse getAccountSchema(@PathVariable String pluginId) {
        return managementService.getAccountSchema(pluginId);
    }

    @PatchMapping("/accounts/enabled")
    public BatchUpdatePluginAccountStateResponse updateAccountsEnabled(
            @PathVariable String pluginId,
            @RequestBody BatchUpdatePluginAccountStateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return managementService.updateAccountsEnabled(pluginId, request, user.username());
    }

    @GetMapping("/accounts/{accountId}")
    public PluginAccountResponse getAccount(@PathVariable String pluginId, @PathVariable String accountId) {
        return managementService.getAccount(pluginId, accountId);
    }

    @PostMapping("/accounts")
    public PluginAccountResponse createAccount(
            @PathVariable String pluginId,
            @RequestBody UpsertPluginAccountRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return managementService.createAccount(pluginId, request, user.username());
    }

    @PutMapping("/accounts/{accountId}")
    public PluginAccountResponse updateAccount(
            @PathVariable String pluginId,
            @PathVariable String accountId,
            @RequestBody(required = false) UpsertPluginAccountRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return managementService.updateAccount(pluginId, accountId, request, user.username());
    }

    @PatchMapping("/accounts/{accountId}/enabled")
    public PluginAccountResponse updateAccountEnabled(
            @PathVariable String pluginId,
            @PathVariable String accountId,
            @RequestBody UpdatePluginAccountStateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return managementService.updateAccountEnabled(pluginId, accountId, request, user.username());
    }

    @DeleteMapping("/accounts/{accountId}")
    public Map<String, Boolean> deleteAccount(
            @PathVariable String pluginId,
            @PathVariable String accountId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        managementService.deleteAccount(pluginId, accountId, user.username());
        return Map.of("success", true);
    }

    private String contentDisposition(String filename) {
        String encoded = java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded;
    }
}
