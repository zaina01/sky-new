package com.sky.job.plugin.runtime;

import com.sky.job.plugin.runtime.dto.PluginRuntimeResponse;
import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/plugins/runtimes")
public class PluginRuntimeController {
    private final PluginRuntimeService runtimeService;

    public PluginRuntimeController(PluginRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @GetMapping
    public PageResponse<PluginRuntimeResponse> list(
            @RequestParam(required = false) String pluginId,
            @RequestParam(required = false) PluginRuntimeState state,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return runtimeService.list(pluginId, state, page, size);
    }

    @GetMapping("/{pluginId}")
    public PluginRuntimeResponse detail(@PathVariable String pluginId) {
        return runtimeService.detail(pluginId);
    }

    @PostMapping("/{pluginId}/start")
    public PluginRuntimeResponse start(@PathVariable String pluginId, @AuthenticationPrincipal AuthenticatedUser user) {
        return runtimeService.start(pluginId, user.username());
    }

    @PostMapping("/{pluginId}/stop")
    public PluginRuntimeResponse stop(@PathVariable String pluginId, @AuthenticationPrincipal AuthenticatedUser user) {
        return runtimeService.stop(pluginId, user.username());
    }

    @PostMapping("/{pluginId}/restart")
    public PluginRuntimeResponse restart(@PathVariable String pluginId, @AuthenticationPrincipal AuthenticatedUser user) {
        return runtimeService.restart(pluginId, user.username());
    }

    @DeleteMapping("/{pluginId}")
    public Map<String, Boolean> uninstall(@PathVariable String pluginId, @AuthenticationPrincipal AuthenticatedUser user) {
        runtimeService.uninstall(pluginId, user.username());
        return Map.of("success", true);
    }
}
