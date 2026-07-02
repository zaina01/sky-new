package com.sky.job.scheduler;

import com.sky.job.scheduler.dto.CreateJobRequest;
import com.sky.job.scheduler.dto.CronValidationResponse;
import com.sky.job.scheduler.dto.JobExecutionResponse;
import com.sky.job.scheduler.dto.JobResponse;
import com.sky.job.scheduler.dto.ManualRunJobRequest;
import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.scheduler.dto.UpdateJobRequest;
import com.sky.job.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/jobs")
public class JobController {
    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    public PageResponse<JobResponse> list(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return jobService.list(page, size);
    }

    @GetMapping("/{id}")
    public JobResponse get(@PathVariable long id) {
        return jobService.get(id);
    }

    @PostMapping
    public JobResponse create(@Valid @RequestBody CreateJobRequest request, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        return jobService.create(request, user.username(), httpRequest);
    }

    @PutMapping("/{id}")
    public JobResponse update(@PathVariable long id, @Valid @RequestBody UpdateJobRequest request, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        return jobService.update(id, request, user.username(), httpRequest);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        jobService.delete(id, user.username(), httpRequest);
        return Map.of("success", true);
    }

    @PostMapping("/{id}/pause")
    public JobResponse pause(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        return jobService.pause(id, user.username(), httpRequest);
    }

    @PostMapping("/{id}/resume")
    public JobResponse resume(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        return jobService.resume(id, user.username(), httpRequest);
    }

    @PostMapping("/{id}/run")
    public Map<String, Long> run(@PathVariable long id, @Valid @RequestBody(required = false) ManualRunJobRequest request, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        return Map.of("executionId", jobService.run(id, request, user.username(), httpRequest));
    }

    @GetMapping("/cron/validate")
    public CronValidationResponse validateCron(@RequestParam String expression) {
        return jobService.validateCron(expression);
    }

    @GetMapping("/cron/next")
    public List<ZonedDateTime> next(@RequestParam String expression, @RequestParam(defaultValue = "5") int count) {
        return jobService.nextFireTimes(expression, count);
    }

    @GetMapping("/{id}/executions")
    public PageResponse<JobExecutionResponse> executions(@PathVariable long id, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return jobService.executions(id, null, null, page, size);
    }
}
