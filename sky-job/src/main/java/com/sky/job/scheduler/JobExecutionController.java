package com.sky.job.scheduler;

import com.sky.job.scheduler.dto.JobExecutionLogResponse;
import com.sky.job.scheduler.dto.JobExecutionResponse;
import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/job-executions")
public class JobExecutionController {
    private final JobService jobService;

    public JobExecutionController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    public PageResponse<JobExecutionResponse> list(
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) String pluginInstallationId,
            @RequestParam(required = false) String pluginId,
            @RequestParam(required = false) JobExecutionStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return jobService.executions(jobId, pluginInstallationId, pluginId, status, user, page, size);
    }

    @GetMapping("/{id}")
    public JobExecutionResponse get(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return jobService.execution(id, user);
    }

    @PostMapping("/{id}/cancel")
    public JobExecutionResponse cancel(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        return jobService.cancelExecution(id, user, httpRequest);
    }

    @GetMapping("/{id}/logs")
    public JobExecutionLogResponse logs(
            @PathVariable long id,
            @RequestParam(required = false) Long offset,
            @RequestParam(required = false) Integer limitBytes,
            @RequestParam(required = false) Integer tailBytes,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return jobService.executionLogs(id, user, offset, limitBytes, tailBytes);
    }
}
