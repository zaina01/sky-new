package com.sky.job.scheduler;

import com.sky.job.audit.SystemLogStore;
import com.sky.job.plugin.runtime.process.ProcessPluginRuntimeProperties;
import com.sky.job.plugin.runtime.process.ProcessPluginRuntimeValidator;
import com.sky.job.plugin.security.PluginCapability;
import com.sky.job.scheduler.dto.CreateJobRequest;
import com.sky.job.scheduler.dto.JobExecutionLogResponse;
import com.sky.job.scheduler.dto.CronValidationResponse;
import com.sky.job.scheduler.dto.JobExecutionResponse;
import com.sky.job.scheduler.dto.JobResponse;
import com.sky.job.scheduler.dto.ManualRunJobRequest;
import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.scheduler.dto.UpdateJobRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class JobService {
    private static final int DEFAULT_LOG_TAIL_BYTES = 256 * 1024;
    private static final int MAX_LOG_TAIL_BYTES = 1024 * 1024;

    private final JobStore jobStore;
    private final JobExecutionStore executionStore;
    private final PluginRuntimeMetadataStore runtimeMetadataStore;
    private final ProcessPluginRuntimeProperties runtimeProperties;
    private final ProcessPluginRuntimeValidator runtimeValidator;
    private final CronUtils cronUtils;
    private final QuartzJobUtils quartzJobUtils;
    private final JobExecutionService executionService;
    private final SystemLogStore systemLogStore;

    public JobService(
            JobStore jobStore,
            JobExecutionStore executionStore,
            PluginRuntimeMetadataStore runtimeMetadataStore,
            ProcessPluginRuntimeProperties runtimeProperties,
            ProcessPluginRuntimeValidator runtimeValidator,
            CronUtils cronUtils,
            QuartzJobUtils quartzJobUtils,
            JobExecutionService executionService,
            SystemLogStore systemLogStore
    ) {
        this.jobStore = jobStore;
        this.executionStore = executionStore;
        this.runtimeMetadataStore = runtimeMetadataStore;
        this.runtimeProperties = runtimeProperties;
        this.runtimeValidator = runtimeValidator;
        this.cronUtils = cronUtils;
        this.quartzJobUtils = quartzJobUtils;
        this.executionService = executionService;
        this.systemLogStore = systemLogStore;
    }

    @Transactional
    public JobResponse create(CreateJobRequest request, String actor, HttpServletRequest httpRequest) {
        cronUtils.validate(request.cronExpression());
        requireTaskPlugin(request.pluginId());
        long id = jobStore.insert(request.jobName(), request.pluginId(), request.cronExpression(), request.misfirePolicy(), request.concurrent(), request.inputJson(), request.timeoutMillis(), request.remark(), actor);
        ScheduledJob job = getJob(id);
        quartzJobUtils.schedule(job);
        systemLogStore.record("JOB", "CREATE_JOB", "INFO", "SUCCESS", "JOB", String.valueOf(id), "Job created", httpRequest);
        return JobResponse.from(job);
    }

    @Transactional
    public JobResponse update(long id, UpdateJobRequest request, String actor, HttpServletRequest httpRequest) {
        getJob(id);
        cronUtils.validate(request.cronExpression());
        requireTaskPlugin(request.pluginId());
        jobStore.update(id, request.jobName(), request.pluginId(), request.cronExpression(), request.misfirePolicy(), request.concurrent(), request.inputJson(), request.timeoutMillis(), request.remark(), actor);
        ScheduledJob job = getJob(id);
        quartzJobUtils.reschedule(job);
        systemLogStore.record("JOB", "UPDATE_JOB", "INFO", "SUCCESS", "JOB", String.valueOf(id), "Job updated", httpRequest);
        return JobResponse.from(job);
    }

    public JobResponse get(long id) {
        return JobResponse.from(getJob(id));
    }

    public PageResponse<JobResponse> list(Integer page, Integer size) {
        return new PageResponse<>(jobStore.list(page, size).stream().map(JobResponse::from).toList(), normalizedPage(page), normalizedSize(size), jobStore.count());
    }

    @Transactional
    public void delete(long id, String actor, HttpServletRequest httpRequest) {
        getJob(id);
        if (executionStore.hasRunningForJob(id)) {
            throw new IllegalArgumentException("Cannot delete a job with running executions");
        }
        quartzJobUtils.delete(id);
        executionStore.deleteByJobId(id);
        jobStore.delete(id);
        systemLogStore.record("JOB", "DELETE_JOB", "INFO", "SUCCESS", "JOB", String.valueOf(id), "Job deleted", httpRequest);
    }

    @Transactional
    public JobResponse pause(long id, String actor, HttpServletRequest httpRequest) {
        getJob(id);
        jobStore.updateStatus(id, JobStatus.DISABLED, actor);
        quartzJobUtils.delete(id);
        systemLogStore.record("JOB", "PAUSE_JOB", "INFO", "SUCCESS", "JOB", String.valueOf(id), "Job paused", httpRequest);
        return get(id);
    }

    @Transactional
    public JobResponse resume(long id, String actor, HttpServletRequest httpRequest) {
        getJob(id);
        jobStore.updateStatus(id, JobStatus.ENABLED, actor);
        ScheduledJob job = getJob(id);
        quartzJobUtils.resume(job);
        systemLogStore.record("JOB", "RESUME_JOB", "INFO", "SUCCESS", "JOB", String.valueOf(id), "Job resumed", httpRequest);
        return JobResponse.from(job);
    }

    public long run(long id, ManualRunJobRequest request, String actor, HttpServletRequest httpRequest) {
        getJob(id);
        long executionId = executionService.execute(id, JobTriggerType.MANUAL, request == null ? null : request.inputJson(), request == null ? null : request.timeoutMillis(), actor);
        systemLogStore.record("JOB", "RUN_JOB", "INFO", "SUCCESS", "JOB", String.valueOf(id), "Job manual run started", httpRequest);
        return executionId;
    }

    public JobExecutionResponse cancelExecution(long id, String actor, HttpServletRequest httpRequest) {
        JobExecution execution = executionService.cancel(id, actor);
        systemLogStore.record("JOB_EXECUTION", "CANCEL_JOB_EXECUTION", "INFO", "SUCCESS", "JOB_EXECUTION", String.valueOf(id), "Job execution cancellation requested", httpRequest);
        return JobExecutionResponse.from(execution);
    }

    public List<ZonedDateTime> nextFireTimes(String expression, int count) {
        return cronUtils.nextFireTimes(expression, count);
    }

    public CronValidationResponse validateCron(String expression) {
        return cronUtils.isValid(expression) ? CronValidationResponse.ok() : CronValidationResponse.error("Invalid cron expression");
    }

    public PageResponse<JobExecutionResponse> executions(Long jobId, String pluginId, JobExecutionStatus status, Integer page, Integer size) {
        return new PageResponse<>(
                executionStore.list(jobId, pluginId, status, page, size).stream().map(JobExecutionResponse::from).toList(),
                normalizedPage(page),
                normalizedSize(size),
                executionStore.count(jobId, pluginId, status)
        );
    }

    public JobExecutionResponse execution(long id) {
        return executionStore.findById(id).map(JobExecutionResponse::from).orElseThrow(() -> new IllegalArgumentException("Job execution not found"));
    }

    public JobExecutionLogResponse executionLogs(long id, Long offset, Integer limitBytes, Integer tailBytes) {
        JobExecution execution = executionStore.findById(id).orElseThrow(() -> new IllegalArgumentException("Job execution not found"));
        int limit = normalizedLimitBytes(limitBytes, tailBytes);
        Path logPath = runnerLogPath(execution);
        if (!Files.isRegularFile(logPath)) {
            return new JobExecutionLogResponse(execution.id(), execution.pluginId(), execution.instanceId(), logPath.toString(), false, "", 0, 0, limit, 0, true, false);
        }
        try {
            long sizeBytes = Files.size(logPath);
            long start = normalizedLogOffset(offset, tailBytes, sizeBytes, limit);
            int bytesToRead = (int) Math.min(limit, Math.max(sizeBytes - start, 0));
            byte[] bytes = new byte[bytesToRead];
            try (RandomAccessFile file = new RandomAccessFile(logPath.toFile(), "r")) {
                file.seek(start);
                file.readFully(bytes);
            }
            long nextOffset = start + bytesToRead;
            boolean eof = nextOffset >= sizeBytes;
            boolean truncated = offset == null && tailBytes != null && start > 0;
            return new JobExecutionLogResponse(execution.id(), execution.pluginId(), execution.instanceId(), logPath.toString(), true, new String(bytes, StandardCharsets.UTF_8), start, nextOffset, limit, sizeBytes, eof, truncated);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read job execution runner log", exception);
        }
    }

    private ScheduledJob getJob(long id) {
        return jobStore.findById(id).orElseThrow(() -> new IllegalArgumentException("Job not found"));
    }

    private void requireTaskPlugin(String pluginId) {
        PluginRuntimeMetadata metadata = runtimeMetadataStore.findByPluginId(pluginId).orElseThrow(() -> new IllegalArgumentException("Plugin runtime not found"));
        if (!metadata.capabilities().contains(PluginCapability.TASK)) {
            throw new IllegalArgumentException("Plugin does not support task execution");
        }
    }

    private Path runnerLogPath(JobExecution execution) {
        Path logRoot = runtimeProperties.logRoot().toAbsolutePath().normalize();
        if (execution.runnerLogPath() == null || execution.runnerLogPath().isBlank()) {
            throw new IllegalStateException("Job execution runner log path is missing");
        }
        Path logPath = Path.of(execution.runnerLogPath()).toAbsolutePath().normalize();
        if (!logPath.startsWith(logRoot) || !"runner.log".equals(logPath.getFileName().toString())) {
            throw new IllegalStateException("Job execution runner log path escapes configured root");
        }
        return logPath;
    }

    private int normalizedLimitBytes(Integer limitBytes, Integer tailBytes) {
        int requested = limitBytes == null ? tailBytes == null ? DEFAULT_LOG_TAIL_BYTES : tailBytes : limitBytes;
        return Math.min(Math.max(requested, 1), MAX_LOG_TAIL_BYTES);
    }

    private long normalizedLogOffset(Long offset, Integer tailBytes, long sizeBytes, int limitBytes) {
        if (offset != null) {
            return Math.min(Math.max(offset, 0), sizeBytes);
        }
        if (tailBytes != null) {
            return Math.max(sizeBytes - limitBytes, 0);
        }
        return 0;
    }

    private int normalizedPage(Integer page) {
        return Math.max(page == null ? 1 : page, 1);
    }

    private int normalizedSize(Integer size) {
        return Math.min(Math.max(size == null ? 20 : size, 1), 100);
    }
}
