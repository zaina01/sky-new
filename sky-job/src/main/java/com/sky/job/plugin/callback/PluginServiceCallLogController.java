package com.sky.job.plugin.callback;

import com.sky.job.plugin.callback.dto.PluginServiceCallLogResponse;
import com.sky.job.plugin.callback.dto.PluginServiceCallRunnerLogResponse;
import com.sky.job.scheduler.dto.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/plugin-service/calls")
public class PluginServiceCallLogController {
    private final PluginServiceCallLogService service;

    public PluginServiceCallLogController(PluginServiceCallLogService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<PluginServiceCallLogResponse> list(
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) String callerPluginId,
            @RequestParam(required = false) String targetPluginId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return service.list(jobId, callerPluginId, targetPluginId, operation, success, page, size);
    }

    @GetMapping("/{id}")
    public PluginServiceCallLogResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/logs")
    public PluginServiceCallRunnerLogResponse runnerLog(
            @PathVariable long id,
            @RequestParam(required = false) Long offset,
            @RequestParam(required = false) Integer limitBytes,
            @RequestParam(required = false) Integer tailBytes
    ) {
        return service.runnerLog(id, offset, limitBytes, tailBytes);
    }
}
