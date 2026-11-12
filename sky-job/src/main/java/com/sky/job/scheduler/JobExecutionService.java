package com.sky.job.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.execution.PluginTaskInvoker;
import com.sky.job.execution.TaskRunRequestFactory;
import com.sky.job.notification.HostNotificationEvent;
import com.sky.job.notification.NotificationEventType;
import com.sky.job.notification.NotificationService;
import com.sky.job.plugin.runtime.PluginRuntimeAdapter;
import com.sky.job.plugin.runtime.PluginRuntimeHandle;
import com.sky.job.plugin.runtime.PluginRuntimeSpec;
import com.sky.job.plugin.runtime.process.ProcessPluginRuntimeProperties;
import com.sky.job.plugin.runtime.process.ProcessPluginRuntimeValidator;
import com.sky.job.plugin.storage.PluginAuditStore;
import com.sky.job.system.DynamicProcessPluginRuntimeSettings;
import com.sky.plugin.protocol.CancelTaskResponse;
import com.sky.plugin.protocol.TaskRunResponse;
import com.sky.plugin.protocol.TaskRunStatus;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JobExecutionService {
    private static final Logger log = LoggerFactory.getLogger(JobExecutionService.class);

    private final JobStore jobStore;
    private final JobExecutionStore executionStore;
    private final PluginRuntimeMetadataStore runtimeMetadataStore;
    private final PluginRuntimeAdapter runtimeAdapter;
    private final TaskRunRequestFactory requestFactory;
    private final PluginTaskInvoker taskInvoker;
    private final ProcessPluginRuntimeProperties runtimeProperties;
    private final DynamicProcessPluginRuntimeSettings runtimeSettings;
    private final PluginAuditStore auditStore;
    private final ProcessPluginRuntimeValidator runtimeValidator;
    private final ObjectMapper objectMapper;
    private final ActiveJobExecutionRegistry activeExecutionRegistry;
    private final NotificationService notificationService;
    private final int hostGrpcPort;

    public JobExecutionService(
            JobStore jobStore,
            JobExecutionStore executionStore,
            PluginRuntimeMetadataStore runtimeMetadataStore,
            PluginRuntimeAdapter runtimeAdapter,
            TaskRunRequestFactory requestFactory,
            PluginTaskInvoker taskInvoker,
            ProcessPluginRuntimeProperties runtimeProperties,
            DynamicProcessPluginRuntimeSettings runtimeSettings,
            PluginAuditStore auditStore,
            ProcessPluginRuntimeValidator runtimeValidator,
            ObjectMapper objectMapper,
            ActiveJobExecutionRegistry activeExecutionRegistry,
            NotificationService notificationService,
            @Value("${sky.plugin.host-grpc.port}") int hostGrpcPort
    ) {
        this.jobStore = jobStore;
        this.executionStore = executionStore;
        this.runtimeMetadataStore = runtimeMetadataStore;
        this.runtimeAdapter = runtimeAdapter;
        this.requestFactory = requestFactory;
        this.taskInvoker = taskInvoker;
        this.runtimeProperties = runtimeProperties;
        this.runtimeSettings = runtimeSettings;
        this.auditStore = auditStore;
        this.runtimeValidator = runtimeValidator;
        this.objectMapper = objectMapper;
        this.activeExecutionRegistry = activeExecutionRegistry;
        this.notificationService = notificationService;
        this.hostGrpcPort = hostGrpcPort;
    }

    public long execute(long jobId, JobTriggerType triggerType, String overrideInputJson, Long overrideTimeoutMillis, String actor) {
        ScheduledJob job = jobStore.findByIdUnscoped(jobId).orElseThrow(() -> new IllegalArgumentException("Job not found"));
        if (triggerType == JobTriggerType.SCHEDULED && job.jobStatus() != JobStatus.ENABLED) {
            return 0;
        }
        PluginRuntimeMetadata metadata = runtimeMetadataStore.findByInstallationId(job.pluginInstallationId()).orElseThrow(() -> new IllegalArgumentException("Plugin installation not found"));
        boolean acquiredExecutionSlot = false;
        if (!job.concurrent()) {
            if (jobStore.markRunningIfIdle(job.id()) == 0) {
                if (triggerType == JobTriggerType.SCHEDULED) {
                    return 0;
                }
                throw new IllegalStateException("Job is already running");
            }
            acquiredExecutionSlot = true;
        }
        try {
            return executeAcquired(job, metadata, triggerType, overrideInputJson, overrideTimeoutMillis, actor);
        } finally {
            if (acquiredExecutionSlot) {
                jobStore.markIdleIfRunning(job.id());
            }
        }
    }

    private long executeAcquired(ScheduledJob job, PluginRuntimeMetadata metadata, JobTriggerType triggerType, String overrideInputJson, Long overrideTimeoutMillis, String actor) {
        if (!metadata.capabilities().contains(com.sky.job.plugin.security.PluginCapability.TASK)) {
            throw new IllegalArgumentException("Plugin does not support task execution");
        }
        String runId = UUID.randomUUID().toString();
        String instanceId = metadata.instanceId() + "-job-" + job.id() + "-exec-" + runId;
        long timeoutMillis = overrideTimeoutMillis == null ? job.timeoutMillis() : overrideTimeoutMillis;
        String inputJson = overrideInputJson == null ? job.inputJson() : overrideInputJson;
        Path runnerLogPath = runtimeValidator.logFilePath(job.pluginId(), instanceId, runId);
        long executionId = executionStore.insertRunning(job, instanceId, triggerType, runnerLogPath.toString());
        LocalDateTime startedAt = LocalDateTime.now();
        auditExecutionStart(job, executionId, instanceId, triggerType, actor, timeoutMillis, inputJson);
        PluginRuntimeHandle handle = null;
        ActiveJobExecution active = null;
        String requestId = "";
        try {
            PluginRuntimeSpec spec = new PluginRuntimeSpec(
                    metadata.installationId(),
                    metadata.pluginId(),
                    instanceId,
                    metadata.mainClass(),
                    metadata.artifactPath(),
                    metadata.artifactSha256(),
                    workDir(metadata.pluginId(), metadata.installationId(), instanceId),
                    hostGrpcPort,
                    metadata.dependencyPaths(),
                    metadata.capabilities(),
                    metadata.remoteDebugEnabled(),
                    metadata.remoteDebugPort(),
                    metadata.remoteDebugSuspend(),
                    runId,
                    job.id(),
                    executionId
            );
            handle = runtimeAdapter.start(spec);
            var request = requestFactory.create(job.pluginId(), Long.toString(job.id()), job.jobName(), timeoutMillis, inputJson);
            requestId = request.getRequestId();
            executionStore.updateRequestId(executionId, requestId);
            active = new ActiveJobExecution(executionId, job.id(), job.jobName(), job.pluginInstallationId(), job.pluginId(), instanceId, requestId, triggerType, handle, startedAt, timeoutMillis);
            activeExecutionRegistry.register(active);
            if (executionStore.findByIdUnscoped(executionId).map(JobExecution::status).orElseThrow(() -> new IllegalArgumentException("Job execution not found")) == JobExecutionStatus.CANCELLED) {
                return executionId;
            }
            TaskRunResponse response = taskInvoker.invoke(handle, request, Duration.ofMillis(timeoutMillis));
            JobExecutionStatus status = status(response.getStatus());
            LocalDateTime finishedAt = LocalDateTime.now();
            if (executionStore.markTerminalIfRunning(
                    executionId,
                    status,
                    response.getMessage(),
                    response.getResultJson(),
                    response.getErrorType(),
                    response.getErrorMessage(),
                    startedAt,
                    finishedAt
            ) == 1) {
                auditExecutionFinish(job, executionId, instanceId, requestId, triggerType, actor, status, timeoutMillis, inputJson, response.getResultJson(), response.getErrorType(), startedAt, finishedAt);
                sendTerminalAlert(job, executionId, instanceId, triggerType, status, response.getErrorType(), response.getErrorMessage(), startedAt, finishedAt);
            }
            return executionId;
        } catch (StatusRuntimeException exception) {
            JobExecutionStatus status = exception.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED ? JobExecutionStatus.TIMEOUT : JobExecutionStatus.FAILED;
            LocalDateTime finishedAt = LocalDateTime.now();
            if (executionStore.markTerminalIfRunning(executionId, status, exception.getStatus().getDescription(), null, exception.getClass().getName(), exception.getMessage(), startedAt, finishedAt) == 1) {
                auditExecutionFinish(job, executionId, instanceId, requestId, triggerType, actor, status, timeoutMillis, inputJson, null, exception.getClass().getName(), startedAt, finishedAt);
                sendTerminalAlert(job, executionId, instanceId, triggerType, status, exception.getClass().getName(), exception.getMessage(), startedAt, finishedAt);
            }
            return executionId;
        } catch (Exception exception) {
            LocalDateTime finishedAt = LocalDateTime.now();
            if (executionStore.markTerminalIfRunning(executionId, JobExecutionStatus.FAILED, exception.getMessage(), null, exception.getClass().getName(), exception.getMessage(), startedAt, finishedAt) == 1) {
                auditExecutionFinish(job, executionId, instanceId, requestId, triggerType, actor, JobExecutionStatus.FAILED, timeoutMillis, inputJson, null, exception.getClass().getName(), startedAt, finishedAt);
                sendTerminalAlert(job, executionId, instanceId, triggerType, JobExecutionStatus.FAILED, exception.getClass().getName(), exception.getMessage(), startedAt, finishedAt);
            }
            return executionId;
        } finally {
            ActiveJobExecution registered = activeExecutionRegistry.remove(executionId).orElse(active);
            if (registered != null) {
                stopRuntime(registered);
            } else if (handle != null) {
                runtimeAdapter.stop(handle, runtimeSettings.stopTimeout());
            }
        }
    }

    public JobExecution cancel(long executionId, String actor) {
        JobExecution execution = executionStore.findByIdUnscoped(executionId).orElseThrow(() -> new IllegalArgumentException("Job execution not found"));
        if (execution.status() == JobExecutionStatus.CANCELLED) {
            return execution;
        }
        if (execution.status() != JobExecutionStatus.RUNNING) {
            throw new IllegalStateException("Job execution is already terminal");
        }
        LocalDateTime finishedAt = LocalDateTime.now();
        if (executionStore.markCancelledIfRunning(executionId, "Job execution cancelled", "USER_CANCELLED", execution.startedAt(), finishedAt) == 0) {
            JobExecution current = executionStore.findByIdUnscoped(executionId).orElseThrow(() -> new IllegalArgumentException("Job execution not found"));
            if (current.status() == JobExecutionStatus.CANCELLED) {
                return current;
            }
            throw new IllegalStateException("Job execution is already terminal");
        }

        ActiveJobExecution active = activeExecutionRegistry.get(executionId).orElse(null);
        boolean runnerCancelAttempted = false;
        boolean runnerCancelAccepted = false;
        boolean runtimeStopAttempted = false;
        String runnerCancelErrorType = "";
        String runnerCancelStatusCode = "";

        if (active != null) {
            active.requestCancel();
            if (active.requestId() != null && !active.requestId().isBlank()) {
                runnerCancelAttempted = true;
                try {
                    CancelTaskResponse response = taskInvoker.cancel(active.handle(), active.requestId(), Long.toString(active.jobId()), Duration.ofSeconds(5));
                    runnerCancelAccepted = response.getCancelled();
                } catch (StatusRuntimeException exception) {
                    runnerCancelErrorType = exception.getClass().getName();
                    runnerCancelStatusCode = exception.getStatus().getCode().name();
                } catch (RuntimeException exception) {
                    runnerCancelErrorType = exception.getClass().getName();
                }
            }
            runtimeStopAttempted = stopRuntime(active);
        }

        auditExecutionCancel(execution, active, actor, finishedAt, runnerCancelAttempted, runnerCancelAccepted, runtimeStopAttempted, runnerCancelErrorType, runnerCancelStatusCode);
        sendCancelAlert(execution, active, runnerCancelErrorType, finishedAt);
        return executionStore.findByIdUnscoped(executionId).orElseThrow(() -> new IllegalArgumentException("Job execution not found"));
    }

    private boolean stopRuntime(ActiveJobExecution active) {
        if (!active.requestStop()) {
            return false;
        }
        runtimeAdapter.stop(active.handle(), runtimeSettings.stopTimeout());
        return true;
    }

    private void sendTerminalAlert(ScheduledJob job, long executionId, String instanceId, JobTriggerType triggerType, JobExecutionStatus status, String errorType, String errorMessage, LocalDateTime startedAt, LocalDateTime finishedAt) {
        NotificationEventType eventType = terminalEventType(status);
        if (eventType == null) {
            return;
        }
        Map<String, String> labels = Map.of(
                "eventType", eventType.name(),
                "status", status.name(),
                "triggerType", triggerType.name(),
                "pluginId", job.pluginId(),
                "jobId", Long.toString(job.id())
        );
        String content = "Job execution " + status.name() + ": jobId=" + job.id()
                + ", jobName=" + job.jobName()
                + ", pluginId=" + job.pluginId()
                + ", executionId=" + executionId
                + ", durationMillis=" + Duration.between(startedAt, finishedAt).toMillis()
                + ", errorType=" + nullToEmpty(errorType)
                + ", errorMessage=" + nullToEmpty(errorMessage);
        notificationService.sendHostAlert(new HostNotificationEvent(
                eventType, "Job execution " + status.name(), content, status.name(), executionId,
                job.pluginInstallationId(), job.pluginId(), instanceId, Long.toString(job.id()),
                job.ownerUserId(), job.ownerDepartmentId(), labels));
    }

    private void sendCancelAlert(JobExecution execution, ActiveJobExecution active, String errorType, LocalDateTime finishedAt) {
        Map<String, String> labels = Map.of(
                "eventType", NotificationEventType.JOB_EXECUTION_CANCELLED.name(),
                "status", JobExecutionStatus.CANCELLED.name(),
                "triggerType", execution.triggerType().name(),
                "pluginId", execution.pluginId(),
                "jobId", Long.toString(execution.jobId())
        );
        String content = "Job execution CANCELLED: jobId=" + execution.jobId()
                + ", jobName=" + execution.jobName()
                + ", pluginId=" + execution.pluginId()
                + ", executionId=" + execution.id()
                + ", durationMillis=" + Duration.between(execution.startedAt(), finishedAt).toMillis()
                + ", errorType=" + nullToEmpty(errorType);
        notificationService.sendHostAlert(new HostNotificationEvent(
                NotificationEventType.JOB_EXECUTION_CANCELLED, "Job execution CANCELLED", content, "CANCELLED",
                execution.id(), execution.pluginInstallationId(), execution.pluginId(),
                active == null ? execution.instanceId() : active.instanceId(), Long.toString(execution.jobId()),
                execution.ownerUserId(), execution.ownerDepartmentId(), labels));
    }

    private NotificationEventType terminalEventType(JobExecutionStatus status) {
        return switch (status) {
            case SUCCESS -> NotificationEventType.JOB_EXECUTION_SUCCESS;
            case FAILED -> NotificationEventType.JOB_EXECUTION_FAILED;
            case TIMEOUT -> NotificationEventType.JOB_EXECUTION_TIMEOUT;
            default -> null;
        };
    }

    private void auditExecutionStart(ScheduledJob job, long executionId, String instanceId, JobTriggerType triggerType, String actor, long timeoutMillis, String inputJson) {
        Map<String, Object> detail = baseExecutionDetail("start", job, executionId, instanceId, triggerType, timeoutMillis, inputJson);
        detail.put("status", JobExecutionStatus.RUNNING.name());
        audit(job.pluginInstallationId(), job.pluginId(), instanceId, actor, "PLUGIN_TASK_EXECUTION_START", detail);
    }

    private void auditExecutionFinish(
            ScheduledJob job,
            long executionId,
            String instanceId,
            String requestId,
            JobTriggerType triggerType,
            String actor,
            JobExecutionStatus status,
            long timeoutMillis,
            String inputJson,
            String resultJson,
            String errorType,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
        Map<String, Object> detail = baseExecutionDetail("finish", job, executionId, instanceId, triggerType, timeoutMillis, inputJson);
        detail.put("requestId", requestId == null ? "" : requestId);
        detail.put("status", status.name());
        detail.put("durationMillis", Duration.between(startedAt, finishedAt).toMillis());
        detail.put("resultPresent", hasPayload(resultJson));
        detail.put("resultBytes", utf8Bytes(resultJson));
        detail.put("errorType", errorType == null ? "" : errorType);
        audit(job.pluginInstallationId(), job.pluginId(), instanceId, actor, "PLUGIN_TASK_EXECUTION_FINISH", detail);
    }

    private void auditExecutionCancel(
            JobExecution execution,
            ActiveJobExecution active,
            String actor,
            LocalDateTime finishedAt,
            boolean runnerCancelAttempted,
            boolean runnerCancelAccepted,
            boolean runtimeStopAttempted,
            String runnerCancelErrorType,
            String runnerCancelStatusCode
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("operation", "cancel");
        detail.put("executionId", execution.id());
        detail.put("jobId", execution.jobId());
        detail.put("jobName", execution.jobName());
        detail.put("pluginId", execution.pluginId());
        detail.put("pluginInstallationId", execution.pluginInstallationId());
        detail.put("instanceId", active == null ? execution.instanceId() : active.instanceId());
        detail.put("requestId", active == null ? nullToEmpty(execution.requestId()) : nullToEmpty(active.requestId()));
        detail.put("triggerType", execution.triggerType().name());
        detail.put("status", JobExecutionStatus.CANCELLED.name());
        detail.put("cancelRequestedBy", actor(actor));
        detail.put("runnerCancelAttempted", runnerCancelAttempted);
        detail.put("runnerCancelAccepted", runnerCancelAccepted);
        detail.put("runtimeStopAttempted", runtimeStopAttempted);
        detail.put("durationMillis", Duration.between(execution.startedAt(), finishedAt).toMillis());
        detail.put("runnerCancelErrorType", nullToEmpty(runnerCancelErrorType));
        detail.put("runnerCancelStatusCode", nullToEmpty(runnerCancelStatusCode));
        audit(execution.pluginInstallationId(), execution.pluginId(), active == null ? execution.instanceId() : active.instanceId(), actor, "PLUGIN_TASK_EXECUTION_CANCEL", detail);
    }

    private Map<String, Object> baseExecutionDetail(String operation, ScheduledJob job, long executionId, String instanceId, JobTriggerType triggerType, long timeoutMillis, String inputJson) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("operation", operation);
        detail.put("executionId", executionId);
        detail.put("jobId", job.id());
        detail.put("jobName", job.jobName());
        detail.put("pluginId", job.pluginId());
        detail.put("pluginInstallationId", job.pluginInstallationId());
        detail.put("instanceId", instanceId);
        detail.put("triggerType", triggerType.name());
        detail.put("timeoutMillis", timeoutMillis);
        detail.put("inputPresent", hasPayload(inputJson));
        detail.put("inputBytes", utf8Bytes(inputJson));
        return detail;
    }

    private void audit(String installationId, String pluginId, String instanceId, String actor, String action, Map<String, Object> detail) {
        try {
            auditStore.insert(installationId, pluginId, instanceId, actor(actor), action, objectMapper.writeValueAsString(detail));
        } catch (JsonProcessingException exception) {
            log.warn("Failed to serialize plugin task execution audit detail: pluginId={}, instanceId={}, action={}", pluginId, instanceId, action, exception);
        } catch (RuntimeException exception) {
            log.warn("Failed to write plugin task execution audit log: pluginId={}, instanceId={}, action={}", pluginId, instanceId, action, exception);
        }
    }

    private String actor(String actor) {
        return actor == null || actor.isBlank() ? "unknown" : actor;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private int utf8Bytes(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private boolean hasPayload(String value) {
        return value != null && !value.isBlank();
    }

    private JobExecutionStatus status(TaskRunStatus status) {
        return switch (status) {
            case TASK_RUN_STATUS_SUCCESS -> JobExecutionStatus.SUCCESS;
            case TASK_RUN_STATUS_TIMEOUT -> JobExecutionStatus.TIMEOUT;
            case TASK_RUN_STATUS_CANCELLED -> JobExecutionStatus.CANCELLED;
            default -> JobExecutionStatus.FAILED;
        };
    }

    private Path workDir(String pluginId, String installationId, String instanceId) {
        return runtimeProperties.workRoot().resolve(pluginId).resolve(installationId).resolve(instanceId);
    }
}
