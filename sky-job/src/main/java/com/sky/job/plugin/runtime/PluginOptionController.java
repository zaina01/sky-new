package com.sky.job.plugin.runtime;

import com.sky.job.plugin.runtime.dto.PluginOptionResponse;
import com.sky.job.scheduler.dto.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/plugins")
public class PluginOptionController {
    private final PluginRuntimeService runtimeService;

    public PluginOptionController(PluginRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @GetMapping("/options")
    public PageResponse<PluginOptionResponse> options(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) PluginRuntimeState state,
            @RequestParam(required = false) PluginOptionUsage usage
    ) {
        return runtimeService.options(page, size, keyword, state, usage);
    }
}
