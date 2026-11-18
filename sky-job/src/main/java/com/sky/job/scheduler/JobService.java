package com.sky.job.scheduler;

import com.sky.job.audit.SystemLogStore;
import com.sky.job.plugin.runtime.process.ProcessPluginRuntimeProperties;
import com.sky.job.plugin.runtime.process.ProcessPluginRuntimeValidator;
import com.sky.job.plugin.security.PluginCapability;
import com.sky.job.plugin.runtime.PluginRuntimeRecord;
import com.sky.job.notification.NotificationChannelStore;
import com.sky.job.scheduler.dto.CreateJobRequest;
import com.sky.job.scheduler.dto.JobExecutionLogResponse;
import com.sky.job.scheduler.dto.CronValidationResponse;
import com.sky.job.scheduler.dto.JobExecutionResponse;
import com.sky.job.scheduler.dto.JobResponse;
import com.sky.job.scheduler.dto.ManualRunJobRequest;
import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.scheduler.dto.UpdateJobRequest;
import com.sky.job.scheduler.dto.ReplaceJobNotificationChannelsRequest;
import com.sky.job.security.AuthenticatedUser;
import com.sky.job.security.DataScopeService;
import com.sky.job.security.EffectiveDataScope;
import com.sky.job.security.PermissionNames;
import com.sky.job.web.ResourceNotFoundException;
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
    private final DataScopeService dataScopeService;
    private final NotificationChannelStore notificationChannelStore;

    public JobService(
            JobStore jobStore,
            JobExecutionStore executionStore,
            PluginRuntimeMetadataStore runtimeMetadataStore,
            ProcessPluginRuntimeProperties runtimeProperties,
            ProcessPluginRuntimeValidator runtimeValidator,
            CronUtils cronUtils,
            QuartzJobUtils quartzJobUtils,
            JobExecutionService executionService,
            SystemLogStore systemLogStore,
            DataScopeService dataScopeService,
            NotificationChannelStore notificationChannelStore
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
        this.dataScopeService = dataScopeService;
        this.notificationChannelStore = notificationChannelStore;
    }

    @Transactional
    public JobResponse create(CreateJobRequest request, AuthenticatedUser user, HttpServletRequest httpRequest) {
        cronUtils.validate(request.cronExpression());
        var installation=requireTaskInstallation(request.pluginInstallationId(), user, PermissionNames.JOB_WRITE);
        List<String> channelIds = request.notificationChannelIds().stream().distinct().toList();
        validateJobChannels(request.pluginInstallationId(), channelIds, user);
        long id = jobStore.insert(request.jobName(), request.pluginInstallationId(), request.cronExpression(), request.misfirePolicy(), request.concurrent(), request.inputJson(), request.timeoutMillis(), request.remark(), user.userId(), user.departmentId(), user.username());
        if("standalone".equals(installation.executionMode()))com.sky.job.plugin.script.ScriptOptionsValidation.validateInput(request.inputJson());
        jobStore.replaceNotificationChannels(id, channelIds, user.username());
        ScheduledJob job = getJobUnscoped(id);
        quartzJobUtils.schedule(job);
        systemLogStore.record("JOB", "CREATE_JOB", "INFO", "SUCCESS", "JOB", String.valueOf(id), "Job created", httpRequest);
        return response(job);
    }

    @Transactional
    public JobResponse update(long id, UpdateJobRequest request, AuthenticatedUser user, HttpServletRequest httpRequest) {
        ScheduledJob previous = getJob(id, user, PermissionNames.JOB_WRITE);
        cronUtils.validate(request.cronExpression());
        var installation=requireTaskInstallation(request.pluginInstallationId(), user, PermissionNames.JOB_WRITE);
        if (!previous.pluginInstallationId().equals(request.pluginInstallationId())) {
            validateJobChannels(request.pluginInstallationId(), jobStore.notificationChannelIds(id), user);
        }
        if("standalone".equals(installation.executionMode()))com.sky.job.plugin.script.ScriptOptionsValidation.validateInput(request.inputJson());
        jobStore.update(id, request.jobName(), request.pluginInstallationId(), request.cronExpression(), request.misfirePolicy(), request.concurrent(), request.inputJson(), request.timeoutMillis(), request.remark(), user.username());
        ScheduledJob job = getJobUnscoped(id);
        quartzJobUtils.reschedule(job);
        systemLogStore.record("JOB", "UPDATE_JOB", "INFO", "SUCCESS", "JOB", String.valueOf(id), "Job updated", httpRequest);
        return response(job);
    }

    public JobResponse get(long id, AuthenticatedUser user) {
        return response(getJob(id, user, PermissionNames.JOB_READ));
    }

    public PageResponse<JobResponse> list(AuthenticatedUser user, Integer page, Integer size) {
        EffectiveDataScope scope = dataScopeService.resolve(user, PermissionNames.JOB_READ);
        return new PageResponse<>(jobStore.list(scope, page, size).stream().map(this::response).toList(), normalizedPage(page), normalizedSize(size), jobStore.count(scope));
    }

    @Transactional
    public void delete(long id, AuthenticatedUser user, HttpServletRequest httpRequest) {
        getJob(id, user, PermissionNames.JOB_DELETE);
        if (executionStore.hasRunningForJob(id)) {
            throw new IllegalArgumentException("Cannot delete a job with running executions");
        }
        quartzJobUtils.delete(id);
        executionStore.deleteByJobId(id);
        jobStore.replaceNotificationChannels(id, List.of(), user.username());
        jobStore.delete(id);
        systemLogStore.record("JOB", "DELETE_JOB", "INFO", "SUCCESS", "JOB", String.valueOf(id), "Job deleted", httpRequest);
    }

    @Transactional
    public JobResponse pause(long id, AuthenticatedUser user, HttpServletRequest httpRequest) {
        getJob(id, user, PermissionNames.JOB_WRITE);
        jobStore.updateStatus(id, JobStatus.DISABLED, user.username());
        quartzJobUtils.delete(id);
        systemLogStore.record("JOB", "PAUSE_JOB", "INFO", "SUCCESS", "JOB", String.valueOf(id), "Job paused", httpRequest);
        return response(getJobUnscoped(id));
    }

    @Transactional
    public JobResponse resume(long id, AuthenticatedUser user, HttpServletRequest httpRequest) {
        getJob(id, user, PermissionNames.JOB_WRITE);
        jobStore.updateStatus(id, JobStatus.ENABLED, user.username());
        ScheduledJob job = getJobUnscoped(id);
        quartzJobUtils.resume(job);
        systemLogStore.record("JOB", "RESUME_JOB", "INFO", "SUCCESS", "JOB", String.valueOf(id), "Job resumed", httpRequest);
        return response(job);
    }

    public long run(long id, ManualRunJobRequest request, AuthenticatedUser user, HttpServletRequest httpRequest) {
        getJob(id, user, PermissionNames.JOB_RUN);
        long executionId = executionService.execute(id, JobTriggerType.MANUAL, request == null ? null : request.inputJson(), request == null ? null : request.timeoutMillis(), user.username());
        systemLogStore.record("JOB", "RUN_JOB", "INFO", "SUCCESS", "JOB", String.valueOf(id), "Job manual run started", httpRequest);
        return executionId;
    }

    public JobExecutionResponse cancelExecution(long id, AuthenticatedUser user, HttpServletRequest httpRequest) {
        requireExecution(id, user, PermissionNames.JOB_EXECUTION_CANCEL);
        JobExecution execution = executionService.cancel(id, user.username());
        systemLogStore.record("JOB_EXECUTION", "CANCEL_JOB_EXECUTION", "INFO", "SUCCESS", "JOB_EXECUTION", String.valueOf(id), "Job execution cancellation requested", httpRequest);
        return JobExecutionResponse.from(execution);
    }

    @Transactional
    public JobResponse replaceNotificationChannels(long id, ReplaceJobNotificationChannelsRequest request, AuthenticatedUser user) {
        ScheduledJob job = getJob(id, user, PermissionNames.JOB_WRITE);
        requirePermissions(user, PermissionNames.JOB_NOTIFICATION_BIND, PermissionNames.NOTIFICATION_CHANNEL_READ);
        List<String> channelIds = request == null ? List.of() : request.channelIds().stream().distinct().toList();
        validateJobChannels(job.pluginInstallationId(), channelIds, user);
        jobStore.replaceNotificationChannels(id, channelIds, user.username());
        return response(job);
    }

    public List<ZonedDateTime> nextFireTimes(String expression, int count) {
        return cronUtils.nextFireTimes(expression, count);
    }

    public CronValidationResponse validateCron(String expression) {
        return cronUtils.isValid(expression) ? CronValidationResponse.ok() : CronValidationResponse.error("Invalid cron expression");
    }

    public PageResponse<JobExecutionResponse> executions(Long jobId, String installationId, String pluginId, JobExecutionStatus status, AuthenticatedUser user, Integer page, Integer size) {
        EffectiveDataScope scope = dataScopeService.resolve(user, PermissionNames.JOB_EXECUTION_READ);
        if (jobId != null && jobStore.findById(jobId, scope).isEmpty()) throw new ResourceNotFoundException("Job not found");
        return new PageResponse<>(
                executionStore.list(jobId, installationId, pluginId, status, scope, page, size).stream().map(JobExecutionResponse::from).toList(),
                normalizedPage(page),
                normalizedSize(size),
                executionStore.count(jobId, installationId, pluginId, status, scope)
        );
    }

    public JobExecutionResponse execution(long id, AuthenticatedUser user) {
        return JobExecutionResponse.from(requireExecution(id, user, PermissionNames.JOB_EXECUTION_READ));
    }

    public JobExecutionLogResponse executionLogs(long id, AuthenticatedUser user, Long offset, Integer limitBytes, Integer tailBytes) {
        JobExecution execution = requireExecution(id, user, PermissionNames.JOB_EXECUTION_READ);
        int limit = normalizedLimitBytes(limitBytes, tailBytes);
        Path logPath = runnerLogPath(execution);
        if (!Files.isRegularFile(logPath)) {
            return new JobExecutionLogResponse(execution.id(), execution.pluginInstallationId(), execution.pluginId(), execution.instanceId(), logPath.toString(), false, "", 0, 0, limit, 0, true, false);
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
            return new JobExecutionLogResponse(execution.id(), execution.pluginInstallationId(), execution.pluginId(), execution.instanceId(), logPath.toString(), true, new String(bytes, StandardCharsets.UTF_8), start, nextOffset, limit, sizeBytes, eof, truncated);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read job execution runner log", exception);
        }
    }

    private ScheduledJob getJob(long id, AuthenticatedUser user, String permissionName) {
        return jobStore.findById(id, dataScopeService.resolve(user, permissionName)).orElseThrow(() -> new ResourceNotFoundException("Job not found"));
    }

    private ScheduledJob getJobUnscoped(long id) {
        return jobStore.findByIdUnscoped(id).orElseThrow(() -> new ResourceNotFoundException("Job not found"));
    }

    private PluginRuntimeRecord requireTaskInstallation(String installationId, AuthenticatedUser user, String permissionName) {
        PluginRuntimeRecord runtime = runtimeMetadataStore.findRuntimeByInstallationId(installationId).orElseThrow(() -> new ResourceNotFoundException("Plugin installation not found"));
        if (!dataScopeService.allows(dataScopeService.resolve(user, permissionName), runtime.ownerUserId(), runtime.ownerDepartmentId())) {
            throw new ResourceNotFoundException("Plugin installation not found");
        }
        if (!runtimeMetadataStore.runtimeCapabilities(runtime.capabilitiesJson()).contains(PluginCapability.TASK)) {
            throw new IllegalArgumentException("Plugin does not support task execution");
        }
        return runtime;
    }

    private JobExecution requireExecution(long id, AuthenticatedUser user, String permissionName) {
        return executionStore.findById(id, dataScopeService.resolve(user, permissionName)).orElseThrow(() -> new ResourceNotFoundException("Job execution not found"));
    }

    private JobResponse response(ScheduledJob job) {
        return JobResponse.from(job, jobStore.notificationChannelIds(job.id()));
    }

    private void validateJobChannels(String installationId, List<String> channelIds, AuthenticatedUser user) {
        if (channelIds == null || channelIds.isEmpty()) return;
        requirePermissions(user, PermissionNames.JOB_NOTIFICATION_BIND, PermissionNames.NOTIFICATION_CHANNEL_READ);
        java.util.Set<String> pool = java.util.Set.copyOf(runtimeMetadataStore.notificationChannelIds(installationId));
        for (String channelId : channelIds) {
            if (!pool.contains(channelId)) throw new IllegalArgumentException("Job notification channel is outside the plugin installation channel pool: " + channelId);
            notificationChannelStore.findByChannelId(channelId, dataScopeService.resolve(user, PermissionNames.NOTIFICATION_CHANNEL_READ))
                    .orElseThrow(() -> new ResourceNotFoundException("Notification channel not found"));
        }
    }

    private void requirePermissions(AuthenticatedUser user, String... permissions) {
        for (String permission : permissions) {
            if (!user.permissions().contains(permission) && !user.roles().contains("ADMIN")) {
                throw new org.springframework.security.access.AccessDeniedException("Missing permission: " + permission);
            }
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
