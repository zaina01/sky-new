package com.sky.job.plugin.runtime;

import com.sky.job.plugin.runtime.dto.PluginRuntimeResponse;
import com.sky.job.plugin.runtime.dto.CreatePluginInstallationRequest;
import com.sky.job.plugin.runtime.dto.ReplaceNotificationChannelsRequest;
import com.sky.job.plugin.runtime.dto.UpdatePluginRuntimeDebugRequest;
import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/plugins/installations")
public class PluginRuntimeController {
    private final PluginRuntimeService runtimeService;

    public PluginRuntimeController(PluginRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @GetMapping
    public PageResponse<PluginRuntimeResponse> list(
            @RequestParam(required = false) String pluginId,
            @RequestParam(required = false) PluginRuntimeState state,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return runtimeService.list(pluginId, state, user, page, size);
    }

    @PostMapping
    public PluginRuntimeResponse create(@Valid @RequestBody CreatePluginInstallationRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        return runtimeService.create(request, user);
    }

    @GetMapping("/{installationId}")
    public PluginRuntimeResponse detail(@PathVariable String installationId, @AuthenticationPrincipal AuthenticatedUser user) {
        return runtimeService.detail(installationId, user);
    }

    @PostMapping("/{installationId}/start")
    public PluginRuntimeResponse start(@PathVariable String installationId, @AuthenticationPrincipal AuthenticatedUser user) {
        return runtimeService.start(installationId, user);
    }

    @PostMapping("/{installationId}/stop")
    public PluginRuntimeResponse stop(@PathVariable String installationId, @AuthenticationPrincipal AuthenticatedUser user) {
        return runtimeService.stop(installationId, user);
    }

    @PostMapping("/{installationId}/restart")
    public PluginRuntimeResponse restart(@PathVariable String installationId, @AuthenticationPrincipal AuthenticatedUser user) {
        return runtimeService.restart(installationId, user);
    }

    @PutMapping("/{installationId}/debug")
    public PluginRuntimeResponse updateDebug(
            @PathVariable String installationId,
            @RequestBody(required = false) UpdatePluginRuntimeDebugRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return runtimeService.updateDebug(installationId, request, user);
    }

    @PutMapping("/{installationId}/notification-channels")
    public List<String> replaceNotificationChannels(
            @PathVariable String installationId,
            @Valid @RequestBody ReplaceNotificationChannelsRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return runtimeService.replaceNotificationChannels(installationId, request, user);
    }

    @GetMapping("/{installationId}/notification-channels")
    public List<String> notificationChannelIds(
            @PathVariable String installationId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return runtimeService.notificationChannelIds(installationId, user);
    }

    @DeleteMapping("/{installationId}")
    public Map<String, Boolean> uninstall(@PathVariable String installationId, @AuthenticationPrincipal AuthenticatedUser user) {
        runtimeService.uninstall(installationId, user);
        return Map.of("success", true);
    }
}
