package com.sky.job.plugin.artifact;

import com.sky.job.plugin.artifact.dto.PluginDefinitionResponse;
import com.sky.job.scheduler.dto.PageResponse;
import org.springframework.stereotype.Service;

@Service
public class PluginDefinitionService {
    private final PluginArtifactRegistry registry;

    public PluginDefinitionService(PluginArtifactRegistry registry) {
        this.registry = registry;
    }

    public PageResponse<PluginDefinitionResponse> list(String keyword, Integer page, Integer size) {
        int p = Math.max(page == null ? 1 : page, 1);
        int s = Math.min(Math.max(size == null ? 20 : size, 1), 100);
        return new PageResponse<>(registry.list(keyword, p, s).stream().map(PluginDefinitionResponse::from).toList(), p, s, registry.count(keyword));
    }

    public PluginDefinitionResponse get(String pluginId) {
        return registry.find(pluginId).map(PluginDefinitionResponse::from).orElseThrow(() -> new com.sky.job.web.ResourceNotFoundException("Plugin definition not found"));
    }

    public void delete(String pluginId) {
        get(pluginId);
        if (registry.hasInstallations(pluginId)) throw new IllegalStateException("Cannot delete a plugin definition with installations");
        if (registry.delete(pluginId) == 0) throw new com.sky.job.web.ResourceNotFoundException("Plugin definition not found");
    }
}
