package com.sky.job.plugin.callback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.execution.PluginTaskInvoker;
import com.sky.job.plugin.runtime.PluginRuntimeHandle;
import com.sky.job.plugin.runtime.PluginRuntimeRecord;
import com.sky.job.plugin.runtime.PluginRuntimeService;
import com.sky.job.plugin.runtime.ServicePluginIdleTracker;
import com.sky.job.plugin.runtime.process.ProcessPluginRuntimeProperties;
import com.sky.job.plugin.security.PluginCapability;
import com.sky.job.plugin.security.PluginRuntimePrincipal;
import com.sky.job.plugin.storage.PluginAuditStore;
import com.sky.job.scheduler.JobExecution;
import com.sky.job.scheduler.JobExecutionStore;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.plugin.protocol.PluginServiceCallRequest;
import com.sky.plugin.protocol.PluginServiceCallResponse;
import com.sky.plugin.protocol.PluginServiceRunRequest;
import com.sky.plugin.protocol.PluginServiceRunResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PluginServiceCallCoordinator {
    private static final Logger log = LoggerFactory.getLogger(PluginServiceCallCoordinator.class);
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final int MAX_REQUEST_BYTES = 256 * 1024;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private final PluginRuntimeMetadataStore runtimeStore;
    private final JobExecutionStore executionStore;
    private final PluginRuntimeService runtimeService;
    private final PluginTaskInvoker taskInvoker;
    private final PluginServicePermissionStore permissionStore;
    private final ServicePluginIdleTracker servicePluginIdleTracker;
    private final PluginServiceCallLogStore callLogStore;
    private final PluginAuditStore auditStore;
    private final ObjectMapper objectMapper;
    private final ProcessPluginRuntimeProperties runtimeProperties;

    public PluginServiceCallCoordinator(
            PluginRuntimeMetadataStore runtimeStore,
            JobExecutionStore executionStore,
            PluginRuntimeService runtimeService,
            PluginTaskInvoker taskInvoker,
            PluginServicePermissionStore permissionStore,
            ServicePluginIdleTracker servicePluginIdleTracker,
            PluginServiceCallLogStore callLogStore,
            PluginAuditStore auditStore,
            ObjectMapper objectMapper,
            ProcessPluginRuntimeProperties runtimeProperties
    ) {
        this.runtimeStore = runtimeStore;
        this.executionStore = executionStore;
        this.runtimeService = runtimeService;
        this.taskInvoker = taskInvoker;
        this.permissionStore = permissionStore;
        this.servicePluginIdleTracker = servicePluginIdleTracker;
        this.callLogStore = callLogStore;
        this.auditStore = auditStore;
        this.objectMapper = objectMapper;
        this.runtimeProperties = runtimeProperties;
    }

    public PluginServiceCallResponse call(PluginRuntimePrincipal principal, PluginServiceCallRequest request) {
        long startedAt = System.currentTimeMillis();
        String serviceRequestId = UUID.randomUUID().toString();
        PluginRuntimeRecord caller = null;
        PluginRuntimeRecord target = null;
        String requestedTargetPluginId = safe(request.getTargetPluginId());
        String requestedTargetInstallationId = safe(request.getTargetInstallationId());
        String operation = safe(request.getOperation());
        long timeoutMillis = timeoutMillis(request.getTimeoutMillis());
        String normalizedRequestJson = request.getRequestJson();
        try {
            validatePrincipal(principal, request);
            caller = callerRuntime(principal);
            validateName(operation, "Invalid plugin service operation");
            normalizedRequestJson = normalizeJson(request.getRequestJson(), MAX_REQUEST_BYTES);
            target = resolveTarget(principal.installationId(), requestedTargetInstallationId, requestedTargetPluginId, operation);
            if (!runtimeStore.runtimeCapabilities(target.capabilitiesJson()).contains(PluginCapability.SERVICE)) {
                throw Status.PERMISSION_DENIED.withDescription("Target plugin does not support service calls").asRuntimeException();
            }
            if (!permissionStore.isAllowed(principal.installationId(), target.installationId(), operation)) {
                throw Status.PERMISSION_DENIED.withDescription("Plugin service call is not allowed").asRuntimeException();
            }

            PluginRuntimeHandle handle = runtimeService.ensureRunningHandle(target.installationId(), "plugin:" + principal.installationId());
            servicePluginIdleTracker.markCalled(target.installationId());
            String targetRunnerLogPath = handle.logPath().toAbsolutePath().normalize().toString();
            PluginServiceRunResponse serviceResponse = taskInvoker.callService(handle, PluginServiceRunRequest.newBuilder()
                    .setRequestId(serviceRequestId)
                    .setCallerInstallationId(principal.installationId())
                    .setCallerPluginId(principal.pluginId())
                    .setTargetInstallationId(target.installationId())
                    .setTargetPluginId(target.pluginId())
                    .setOperation(operation)
                    .setRequestJson(normalizedRequestJson)
                    .setTimeoutMillis(timeoutMillis)
                    .build(), Duration.ofMillis(timeoutMillis));
            validateSize(serviceResponse.getResultJson(), MAX_RESPONSE_BYTES, "Plugin service response is too large");
            PluginServiceCallResponse response = PluginServiceCallResponse.newBuilder()
                    .setSuccess(serviceResponse.getSuccess())
                    .setResultJson(serviceResponse.getResultJson())
                    .setMessage(serviceResponse.getMessage())
                    .setErrorType(serviceResponse.getErrorType())
                    .setErrorMessage(serviceResponse.getErrorMessage())
                    .build();
            recordCallLog(principal, request, caller, target, handle.instanceId(), operation, serviceRequestId, targetRunnerLogPath,
                    timeoutMillis, normalizedRequestJson, response.getResultJson(), response.getSuccess(), response.getErrorType(), response.getErrorMessage(), startedAt);
            audit(principal, target, operation, request.getRequestId(), serviceRequestId, timeoutMillis, normalizedRequestJson,
                    response.getResultJson(), response.getSuccess(), response.getErrorType(), startedAt);
            return response;
        } catch (RuntimeException exception) {
            recordCallLog(principal, request, caller, target, "", operation, serviceRequestId, "", timeoutMillis,
                    normalizedRequestJson, "", false, exception.getClass().getName(), errorMessage(exception), startedAt);
            audit(principal, target, operation, request.getRequestId(), serviceRequestId, timeoutMillis, normalizedRequestJson,
                    "", false, exception.getClass().getName(), startedAt);
            if (exception instanceof StatusRuntimeException statusRuntimeException) {
                throw statusRuntimeException;
            }
            return PluginServiceCallResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Plugin service call failed")
                    .setErrorType(exception.getClass().getName())
                    .setErrorMessage(errorMessage(exception))
                    .build();
        }
    }

    private PluginRuntimeRecord callerRuntime(PluginRuntimePrincipal principal) {
        PluginRuntimeRecord caller = runtimeStore.findRuntimeByInstallationId(principal.installationId())
                .orElseThrow(() -> Status.PERMISSION_DENIED.withDescription("Caller plugin installation is not available").asRuntimeException());
        if (!caller.pluginId().equals(principal.pluginId())) {
            throw Status.PERMISSION_DENIED.withDescription("Caller plugin runtime identity mismatch").asRuntimeException();
        }
        Long executionId = principal.executionId();
        if (executionId == null) {
            throw Status.PERMISSION_DENIED.withDescription("Caller execution context is not available").asRuntimeException();
        }
        JobExecution execution = executionStore.findByIdUnscoped(executionId)
                .orElseThrow(() -> Status.PERMISSION_DENIED.withDescription("Caller execution context is not available").asRuntimeException());
        if (execution.jobId() != principal.jobId()
                || !execution.pluginInstallationId().equals(principal.installationId())
                || !execution.pluginId().equals(principal.pluginId())
                || !execution.instanceId().equals(principal.instanceId())) {
            throw Status.PERMISSION_DENIED.withDescription("Caller plugin runtime identity mismatch").asRuntimeException();
        }
        return caller;
    }

    private PluginRuntimeRecord resolveTarget(
            String callerInstallationId,
            String targetInstallationId,
            String targetPluginId,
            String operation
    ) {
        if (!targetInstallationId.isBlank()) {
            validateName(targetInstallationId, "Invalid target installation id");
            if (!permissionStore.isAllowed(callerInstallationId, targetInstallationId, operation)) {
                throw Status.PERMISSION_DENIED.withDescription("Plugin service call is not allowed").asRuntimeException();
            }
            PluginRuntimeRecord target = runtimeStore.findRuntimeByInstallationId(targetInstallationId)
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("Target plugin installation not found").asRuntimeException());
            if (!targetPluginId.isBlank() && !target.pluginId().equals(targetPluginId)) {
                throw Status.PERMISSION_DENIED.withDescription("Target plugin identity mismatch").asRuntimeException();
            }
            return target;
        }

        validateName(targetPluginId, "Invalid target plugin id");
        List<String> matches = permissionStore.authorizedTargetInstallationIds(callerInstallationId, targetPluginId, operation)
                .stream().distinct().toList();
        if (matches.isEmpty()) {
            throw Status.PERMISSION_DENIED.withDescription("Plugin service call is not allowed").asRuntimeException();
        }
        if (matches.size() != 1) {
            throw Status.FAILED_PRECONDITION
                    .withDescription("Multiple authorized target installations match; targetInstallationId is required")
                    .asRuntimeException();
        }
        return runtimeStore.findRuntimeByInstallationId(matches.getFirst())
                .orElseThrow(() -> Status.NOT_FOUND.withDescription("Target plugin installation not found").asRuntimeException());
    }

    private void validatePrincipal(PluginRuntimePrincipal principal, PluginServiceCallRequest request) {
        String requestedCaller = safe(request.getCallerPluginId());
        if (!requestedCaller.isBlank() && !principal.pluginId().equals(requestedCaller)) {
            throw Status.PERMISSION_DENIED.withDescription("Plugin identity mismatch").asRuntimeException();
        }
        if (!principal.hasCapability(PluginCapability.TASK) && !principal.hasCapability(PluginCapability.SERVICE)) {
            throw Status.PERMISSION_DENIED.withDescription("Plugin service caller capability required").asRuntimeException();
        }
        if (!principal.taskBound()) {
            throw Status.PERMISSION_DENIED.withDescription("Nested plugin service calls are not allowed").asRuntimeException();
        }
        if (!Long.toString(principal.jobId()).equals(safe(request.getJobId()))) {
            throw Status.PERMISSION_DENIED.withDescription("Job context mismatch").asRuntimeException();
        }
    }

    private void validateName(String value, String message) {
        if (!NAME_PATTERN.matcher(value).matches()) {
            throw Status.INVALID_ARGUMENT.withDescription(message).asRuntimeException();
        }
    }

    private String normalizeJson(String json, int maxBytes) {
        String value = json == null || json.isBlank() ? "{}" : json;
        validateSize(value, maxBytes, "Plugin service request is too large");
        try {
            String normalized = objectMapper.writeValueAsString(objectMapper.readTree(value));
            validateSize(normalized, maxBytes, "Plugin service request is too large");
            return normalized;
        } catch (JsonProcessingException exception) {
            throw Status.INVALID_ARGUMENT.withDescription("Invalid plugin service request JSON").withCause(exception).asRuntimeException();
        }
    }

    private void validateSize(String value, int maxBytes, String message) {
        if (bytes(value) > maxBytes) {
            throw Status.INVALID_ARGUMENT.withDescription(message).asRuntimeException();
        }
    }

    private long timeoutMillis(long requested) {
        long defaultTimeoutMillis = runtimeProperties.serviceCallTimeout().toMillis();
        long maxTimeoutMillis = runtimeProperties.maxServiceCallTimeout().toMillis();
        return requested <= 0 ? defaultTimeoutMillis : Math.min(requested, maxTimeoutMillis);
    }

    private void recordCallLog(
            PluginRuntimePrincipal principal,
            PluginServiceCallRequest request,
            PluginRuntimeRecord caller,
            PluginRuntimeRecord target,
            String targetInstanceId,
            String operation,
            String serviceRequestId,
            String targetRunnerLogPath,
            long timeoutMillis,
            String requestJson,
            String responseJson,
            boolean success,
            String errorType,
            String errorMessage,
            long startedAt
    ) {
        try {
            callLogStore.insert(new PluginServiceCallLogCommand(
                    principal.jobId(),
                    principal.executionId(),
                    principal.installationId(),
                    principal.pluginId(),
                    caller == null ? null : caller.ownerUserId(),
                    caller == null ? null : caller.ownerDepartmentId(),
                    principal.instanceId(),
                    safeValue(request.getRequestId()),
                    target == null ? null : target.installationId(),
                    target == null ? nullable(request.getTargetPluginId()) : target.pluginId(),
                    target == null ? null : target.ownerUserId(),
                    target == null ? null : target.ownerDepartmentId(),
                    safeValue(targetInstanceId),
                    safeValue(operation),
                    serviceRequestId,
                    safeValue(targetRunnerLogPath),
                    timeoutMillis,
                    System.currentTimeMillis() - startedAt,
                    bytes(requestJson),
                    bytes(responseJson),
                    success,
                    safeValue(errorType),
                    truncate(safeValue(errorMessage), 1024)
            ));
        } catch (Exception exception) {
            log.warn("Failed to write plugin service call log: callerInstallationId={}, targetInstallationId={}, operation={}",
                    principal.installationId(), target == null ? "" : target.installationId(), operation, exception);
        }
    }

    private void audit(
            PluginRuntimePrincipal principal,
            PluginRuntimeRecord target,
            String operation,
            String callerRequestId,
            String serviceRequestId,
            long timeoutMillis,
            String requestJson,
            String responseJson,
            boolean success,
            String errorType,
            long startedAt
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("callerInstallationId", principal.installationId());
        detail.put("callerPluginId", principal.pluginId());
        detail.put("callerInstanceId", principal.instanceId());
        detail.put("targetInstallationId", target == null ? "" : target.installationId());
        detail.put("targetPluginId", target == null ? "" : target.pluginId());
        detail.put("operation", operation);
        detail.put("callerRequestId", callerRequestId == null ? "" : callerRequestId);
        detail.put("serviceRequestId", serviceRequestId);
        detail.put("jobId", principal.jobId());
        detail.put("executionId", principal.executionId());
        detail.put("timeoutMillis", timeoutMillis);
        detail.put("requestBytes", bytes(requestJson));
        detail.put("responseBytes", bytes(responseJson));
        detail.put("success", success);
        detail.put("errorType", errorType == null ? "" : errorType);
        detail.put("durationMillis", System.currentTimeMillis() - startedAt);
        try {
            auditStore.insert(principal.installationId(), principal.pluginId(), principal.instanceId(),
                    "plugin:" + principal.installationId(), "PLUGIN_SERVICE_CALL", objectMapper.writeValueAsString(detail));
        } catch (Exception exception) {
            log.warn("Failed to write plugin service call audit: callerInstallationId={}, targetInstallationId={}, operation={}",
                    principal.installationId(), target == null ? "" : target.installationId(), operation, exception);
        }
    }

    private int bytes(String value) {
        return (value == null ? "" : value).getBytes(StandardCharsets.UTF_8).length;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private String nullable(String value) {
        String normalized = safe(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String errorMessage(RuntimeException exception) {
        return exception.getMessage() == null ? "" : exception.getMessage();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
