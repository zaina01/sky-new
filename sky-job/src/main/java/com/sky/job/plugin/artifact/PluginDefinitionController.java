package com.sky.job.plugin.artifact;

import com.sky.job.plugin.artifact.dto.PluginDefinitionResponse;
import com.sky.job.scheduler.dto.PageResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/plugins/definitions")
public class PluginDefinitionController {
    private final PluginDefinitionService service;

    public PluginDefinitionController(PluginDefinitionService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PluginDefinitionResponse> list(@RequestParam(required = false) String keyword, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return service.list(keyword, page, size);
    }

    @GetMapping("/{pluginId}")
    public PluginDefinitionResponse get(@PathVariable String pluginId) {
        return service.get(pluginId);
    }

    @DeleteMapping("/{pluginId}")
    public Map<String, Boolean> delete(@PathVariable String pluginId) {
        service.delete(pluginId);
        return Map.of("success", true);
    }
}
