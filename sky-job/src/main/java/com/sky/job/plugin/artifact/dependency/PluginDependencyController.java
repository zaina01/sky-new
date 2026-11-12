package com.sky.job.plugin.artifact.dependency;

import com.sky.job.plugin.artifact.dependency.dto.PluginDependencyInstallRequest;
import com.sky.job.plugin.artifact.dependency.dto.PluginDependencyInstallResponse;
import com.sky.job.plugin.artifact.dependency.dto.PluginDependencyResponse;
import com.sky.job.plugin.artifact.dependency.dto.PluginDependencyUninstallRequest;
import com.sky.job.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/plugins/definitions/{pluginId}/dependencies")
public class PluginDependencyController {
    private final PluginDependencyManagementService service;

    public PluginDependencyController(PluginDependencyManagementService service) {
        this.service = service;
    }

    @GetMapping
    public List<PluginDependencyResponse> list(@PathVariable String pluginId) {
        return service.list(pluginId);
    }

    @PostMapping("/install")
    public PluginDependencyInstallResponse install(
            @PathVariable String pluginId,
            @RequestBody(required = false) PluginDependencyInstallRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.install(pluginId, request, user.username());
    }

    @PostMapping("/reinstall")
    public PluginDependencyInstallResponse reinstall(@PathVariable String pluginId, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.reinstall(pluginId, user.username());
    }

    @DeleteMapping
    public PluginDependencyInstallResponse uninstall(
            @PathVariable String pluginId,
            @RequestBody(required = false) PluginDependencyUninstallRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.uninstall(pluginId, request, user.username());
    }
}
