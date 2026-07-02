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
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.plugin.protocol.PluginServiceCallRequest;
import com.sky.plugin.protocol.PluginServiceCallResponse;
import com.sky.plugin.protocol.PluginServiceRunRequest;
import com.sky.plugin.protocol.PluginServiceRunResponse;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
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
        String callerPluginId = principal.pluginId();
        String targetPluginId = safe(request.getTargetPluginId());
        String operation = safe(request.getOperation());
        long timeoutMillis = timeoutMillis(request.getTimeoutMillis());
        try {
            validatePrincipal(principal, request);
            validateName(targetPluginId, "Invalid target plugin id");
            validateName(operation, "Invalid plugin service operation");
            String requestJson = normalizeJson(request.getRequestJson(), MAX_REQUEST_BYTES);
            PluginRuntimeRecord target = runtimeStore.findRuntimeByPluginId(targetPluginId).orElseThrow(() -> Status.NOT_FOUND.withDescription("Target plugin runtime not found").asRuntimeException());
            if (!runtimeStore.runtimeCapabilities(target.capabilitiesJson()).contains(PluginCapability.SERVICE)) {
                throw Status.PERMISSION_DENIED.withDescription("Target plugin does not support service calls").asRuntimeException();
            }
            if (!permissionStore.isAllowed(callerPluginId, targetPluginId, operation)) {
                throw Status.PERMISSION_DENIED.withDescription("Plugin service call is not allowed").asRuntimeException();
            }
            PluginRuntimeHandle handle = runtimeService.ensureRunningHandle(targetPluginId, "plugin:" + callerPluginId);
            servicePluginIdleTracker.markCalled(targetPluginId);
            String targetRunnerLogPath = handle.logPath().toAbsolutePath().normalize().toString();
            PluginServiceRunResponse serviceResponse = taskInvoker.callService(handle, PluginServiceRunRequest.newBuilder()
                    .setRequestId(serviceRequestId)
                    .setCallerPluginId(callerPluginId)
                    .setTargetPluginId(targetPluginId)
                    .setOperation(operation)
                    .setRequestJson(requestJson)
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
            recordCallLog(request, callerPluginId, principal.instanceId(), targetPluginId, handle.instanceId(), operation, serviceRequestId, targetRunnerLogPath, timeoutMillis, requestJson, response.getResultJson(), response.getSuccess(), response.getErrorType(), response.getErrorMessage(), startedAt);
            audit(callerPluginId, principal.instanceId(), targetPluginId, operation, request.getRequestId(), serviceRequestId, timeoutMillis, requestJson, response.getResultJson(), response.getSuccess(), response.getErrorType(), startedAt);
            return response;
        } catch (RuntimeException exception) {
            PluginServiceCallResponse response = PluginServiceCallResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Plugin service call failed")
                    .setErrorType(exception.getClass().getName())
                    .setErrorMessage(exception.getMessage() == null ? "" : exception.getMessage())
                    .build();
            recordCallLog(request, callerPluginId, principal.instanceId(), targetPluginId, "", operation, serviceRequestId, "", timeoutMillis, request.getRequestJson(), "", false, exception.getClass().getName(), response.getErrorMessage(), startedAt);
            audit(callerPluginId, principal.instanceId(), targetPluginId, operation, request.getRequestId(), serviceRequestId, timeoutMillis, request.getRequestJson(), "", false, exception.getClass().getName(), startedAt);
            return response;
        }
    }

    private void validatePrincipal(PluginRuntimePrincipal principal, PluginServiceCallRequest request) {
        String requestedCaller = safe(request.getCallerPluginId());
        if (!requestedCaller.isBlank() && !principal.pluginId().equals(requestedCaller)) {
            throw Status.PERMISSION_DENIED.withDescription("Plugin identity mismatch").asRuntimeException();
        }
        if (!principal.hasCapability(PluginCapability.TASK) && !principal.hasCapability(PluginCapability.SERVICE)) {
            throw Status.PERMISSION_DENIED.withDescription("Plugin service caller capability required").asRuntimeException();
        }
        if (safe(request.getJobId()).isBlank()) {
            throw Status.PERMISSION_DENIED.withDescription("Nested plugin service calls are not allowed").asRuntimeException();
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
        if ((value == null ? "" : value).getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw Status.INVALID_ARGUMENT.withDescription(message).asRuntimeException();
        }
    }

    private long timeoutMillis(long requested) {
        long defaultTimeoutMillis = runtimeProperties.serviceCallTimeout().toMillis();
        long maxTimeoutMillis = runtimeProperties.maxServiceCallTimeout().toMillis();
        if (requested <= 0) {
            return defaultTimeoutMillis;
        }
        return Math.min(requested, maxTimeoutMillis);
    }

    private void recordCallLog(PluginServiceCallRequest request, String callerPluginId, String callerInstanceId, String targetPluginId, String targetInstanceId, String operation, String serviceRequestId, String targetRunnerLogPath, long timeoutMillis, String requestJson, String responseJson, boolean success, String errorType, String errorMessage, long startedAt) {
        try {
            callLogStore.insert(new PluginServiceCallLogCommand(
                    jobId(request.getJobId()),
                    safeValue(callerPluginId),
                    safeValue(callerInstanceId),
                    safeValue(request.getRequestId()),
                    safeValue(targetPluginId),
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
            log.warn("Failed to write plugin service call log: caller={}, target={}, operation={}", callerPluginId, targetPluginId, operation, exception);
        }
    }

    private Long jobId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private void audit(String callerPluginId, String callerInstanceId, String targetPluginId, String operation, String callerRequestId, String serviceRequestId, long timeoutMillis, String requestJson, String responseJson, boolean success, String errorType, long startedAt) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("callerPluginId", callerPluginId);
        detail.put("callerInstanceId", callerInstanceId);
        detail.put("targetPluginId", targetPluginId);
        detail.put("operation", operation);
        detail.put("callerRequestId", callerRequestId == null ? "" : callerRequestId);
        detail.put("serviceRequestId", serviceRequestId);
        detail.put("timeoutMillis", timeoutMillis);
        detail.put("requestBytes", bytes(requestJson));
        detail.put("responseBytes", bytes(responseJson));
        detail.put("success", success);
        detail.put("errorType", errorType == null ? "" : errorType);
        detail.put("durationMillis", System.currentTimeMillis() - startedAt);
        try {
            auditStore.insert(callerPluginId, callerInstanceId, "plugin:" + callerPluginId, "PLUGIN_SERVICE_CALL", objectMapper.writeValueAsString(detail));
        } catch (Exception exception) {
            log.warn("Failed to write plugin service call audit: caller={}, target={}, operation={}", callerPluginId, targetPluginId, operation, exception);
        }
    }

    private int bytes(String value) {
        return (value == null ? "" : value).getBytes(StandardCharsets.UTF_8).length;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
