package com.sky.job.plugin.callback;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.notification.NotificationSendCommand;
import com.sky.job.notification.NotificationService;
import com.sky.job.notification.NotificationSourceType;
import com.sky.job.plugin.security.PluginCapability;
import com.sky.job.plugin.security.PluginRuntimePrincipal;
import com.sky.job.plugin.storage.PluginAccountStore;
import com.sky.job.plugin.storage.PluginConfigStore;
import com.sky.job.plugin.storage.PluginDataStore;
import com.sky.job.plugin.storage.PluginLogStore;
import com.sky.job.plugin.storage.PluginPayloadValidator;
import com.sky.job.system.DynamicNotificationSettings;
import com.sky.plugin.protocol.AccountRequest;
import com.sky.plugin.protocol.AccountResponse;
import com.sky.plugin.protocol.ConfigRequest;
import com.sky.plugin.protocol.ConfigResponse;
import com.sky.plugin.protocol.NotificationChannelInfo;
import com.sky.plugin.protocol.NotificationChannelListRequest;
import com.sky.plugin.protocol.NotificationChannelListResponse;
import com.sky.plugin.protocol.NotificationRequest;
import com.sky.plugin.protocol.NotificationResponse;
import com.sky.plugin.protocol.PluginDataRequest;
import com.sky.plugin.protocol.PluginDataResponse;
import com.sky.plugin.protocol.PluginLogEvent;
import com.sky.plugin.protocol.PluginServiceCallRequest;
import com.sky.plugin.protocol.PluginServiceCallResponse;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.StringJoiner;

@Service
public class PluginCallbackService {
    private static final Logger log = LoggerFactory.getLogger(PluginCallbackService.class);
    private static final int MAX_LOG_VALUE_CHARS = 500;
    private static final int MAX_RECEIPT_SUMMARY_CHARS = 2_000;

    private final PluginConfigStore configStore;
    private final PluginAccountStore accountStore;
    private final PluginDataStore dataStore;
    private final PluginPayloadValidator payloadValidator;
    private final PluginLogStore logStore;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final DynamicNotificationSettings notificationSettings;
    private final PluginServiceCallCoordinator serviceCallCoordinator;

    public PluginCallbackService(
            PluginConfigStore configStore,
            PluginAccountStore accountStore,
            PluginDataStore dataStore,
            PluginPayloadValidator payloadValidator,
            PluginLogStore logStore,
            ObjectMapper objectMapper,
            NotificationService notificationService,
            DynamicNotificationSettings notificationSettings,
            PluginServiceCallCoordinator serviceCallCoordinator
    ) {
        this.configStore = configStore;
        this.accountStore = accountStore;
        this.dataStore = dataStore;
        this.payloadValidator = payloadValidator;
        this.logStore = logStore;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.notificationSettings = notificationSettings;
        this.serviceCallCoordinator = serviceCallCoordinator;
    }

    public void writeLog(PluginRuntimePrincipal principal, PluginLogEvent event) {
        verifyPluginId(principal, event.getPluginId());
        if (!event.getInstanceId().isBlank() && !principal.instanceId().equals(event.getInstanceId())) {
            throw Status.PERMISSION_DENIED.withDescription("Plugin instance mismatch").asRuntimeException();
        }
        verifyJobContext(principal, event.getJobId());
        logStore.insert(principal, event, event.getMessage());
    }

    public ConfigResponse getConfig(PluginRuntimePrincipal principal, ConfigRequest request) {
        require(principal, PluginCapability.CONFIG);
        verifyPluginId(principal, request.getPluginId());
        return ConfigResponse.newBuilder()
                .setSuccess(true)
                .setConfigJson(configStore.getConfigJson(principal.installationId()))
                .build();
    }

    public ConfigResponse updateConfig(PluginRuntimePrincipal principal, ConfigRequest request) {
        require(principal, PluginCapability.CONFIG);
        verifyPluginId(principal, request.getPluginId());
        String configJson = payloadValidator.normalizeConfigJson(request.getConfigJson());
        configStore.upsertConfigJson(principal.installationId(), configJson);
        return ConfigResponse.newBuilder().setSuccess(true).setConfigJson(configJson).build();
    }

    public AccountResponse listAccounts(PluginRuntimePrincipal principal, AccountRequest request) {
        require(principal, PluginCapability.ACCOUNT);
        verifyPluginId(principal, request.getPluginId());
        return AccountResponse.newBuilder()
                .setSuccess(true)
                .addAllAccounts(accountStore.listEnabled(principal.installationId()))
                .build();
    }

    public AccountResponse getAccount(PluginRuntimePrincipal principal, AccountRequest request) {
        require(principal, PluginCapability.ACCOUNT);
        verifyPluginId(principal, request.getPluginId());
        payloadValidator.validateAccountId(request.getAccountId());
        return accountStore.getEnabled(principal.installationId(), request.getAccountId())
                .map(account -> AccountResponse.newBuilder().setSuccess(true).addAccounts(account).build())
                .orElseGet(() -> AccountResponse.newBuilder().setSuccess(false).setMessage("Account not found").build());
    }

    public AccountResponse updateAccount(PluginRuntimePrincipal principal, AccountRequest request) {
        require(principal, PluginCapability.ACCOUNT);
        verifyPluginId(principal, request.getPluginId());
        payloadValidator.validateAccountId(request.getAccountId());
        String accountJson = payloadValidator.normalizeAccountJson(request.getAccountJson());
        accountStore.upsert(principal.installationId(), request.getAccountId(), accountJson);
        return AccountResponse.newBuilder().setSuccess(true).build();
    }

    public PluginDataResponse getData(PluginRuntimePrincipal principal, PluginDataRequest request) {
        verifyPluginId(principal, request.getPluginId());
        payloadValidator.validateCollection(request.getCollection());
        payloadValidator.validateKey(request.getKey());
        return dataStore.select(principal.installationId(), request.getCollection(), request.getKey())
                .map(dataJson -> PluginDataResponse.newBuilder().setSuccess(true).setDataJson(dataJson).build())
                .orElseGet(() -> PluginDataResponse.newBuilder().setSuccess(false).setMessage("Data not found").build());
    }

    public PluginDataResponse putData(PluginRuntimePrincipal principal, PluginDataRequest request) {
        verifyPluginId(principal, request.getPluginId());
        payloadValidator.validateCollection(request.getCollection());
        payloadValidator.validateKey(request.getKey());
        String dataJson = payloadValidator.normalizeDataJson(request.getDataJson());
        dataStore.upsert(principal.installationId(), request.getCollection(), request.getKey(), dataJson);
        return PluginDataResponse.newBuilder().setSuccess(true).setDataJson(dataJson).build();
    }

    public PluginDataResponse deleteData(PluginRuntimePrincipal principal, PluginDataRequest request) {
        verifyPluginId(principal, request.getPluginId());
        payloadValidator.validateCollection(request.getCollection());
        payloadValidator.validateKey(request.getKey());
        boolean deleted = dataStore.delete(principal.installationId(), request.getCollection(), request.getKey());
        return PluginDataResponse.newBuilder().setSuccess(true).setDeleted(deleted).build();
    }

    public PluginDataResponse listData(PluginRuntimePrincipal principal, PluginDataRequest request) {
        verifyPluginId(principal, request.getPluginId());
        payloadValidator.validateCollection(request.getCollection());
        PluginDataResponse.Builder builder = PluginDataResponse.newBuilder().setSuccess(true);
        try {
            JsonNode root = objectMapper.readTree(dataStore.selectAll(principal.installationId(), request.getCollection()));
            if (!root.isArray()) {
                throw new IllegalStateException("Plugin data list payload must be an array");
            }
            for (JsonNode item : root) {
                builder.addRecords(com.sky.plugin.protocol.PluginDataRecord.newBuilder()
                        .setKey(item.path("key").asText(""))
                        .setDataJson(item.path("json").toString())
                        .build());
            }
            return builder.build();
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse plugin data list", exception);
        }
    }

    public NotificationChannelListResponse listNotificationChannels(PluginRuntimePrincipal principal, NotificationChannelListRequest request) {
        require(principal, PluginCapability.NOTIFICATION);
        verifyPluginId(principal, request.getPluginId());
        NotificationChannelListResponse.Builder builder = NotificationChannelListResponse.newBuilder();
        notificationService.listAuthorizedChannels(principal).forEach(channel -> builder.addChannels(NotificationChannelInfo.newBuilder()
                .setChannelId(channel.channelId())
                .setChannelType(channel.channelType().name())
                .setDisplayName(channel.displayName() == null ? "" : channel.displayName())
                .setEnabled(channel.enabled())
                .build()));
        return builder.build();
    }

    public PluginServiceCallResponse callPluginService(PluginRuntimePrincipal principal, PluginServiceCallRequest request) {
        return serviceCallCoordinator.call(principal, request);
    }

    public NotificationResponse sendNotification(PluginRuntimePrincipal principal, NotificationRequest request) {
        require(principal, PluginCapability.NOTIFICATION);
        verifyPluginId(principal, request.getPluginId());
        if (request.getInstanceId() != null && !request.getInstanceId().isBlank() && !principal.instanceId().equals(request.getInstanceId())) {
            throw Status.PERMISSION_DENIED.withDescription("Plugin instance mismatch").asRuntimeException();
        }
        verifyJobContext(principal, request.getJobId());
        try {
            List<com.sky.job.notification.NotificationSendReceipt> receipts = notificationService.sendFromPlugin(principal, new NotificationSendCommand(
                    NotificationSourceType.PLUGIN_RUNTIME,
                    request.getRequestId(),
                    principal.installationId(),
                    principal.pluginId(),
                    principal.instanceId(),
                    request.getJobId(),
                    principal.executionId(),
                    null,
                    null,
                    request.getChannelIdsList(),
                    request.getTitle(),
                    request.getContent(),
                    request.getSeverity(),
                    request.getLabelsMap()
            ));
            NotificationResponse.Builder builder = NotificationResponse.newBuilder()
                    .setSuccess(receipts.stream().noneMatch(receipt -> receipt.status() == com.sky.job.notification.NotificationSendStatus.FAILED || receipt.status() == com.sky.job.notification.NotificationSendStatus.SKIPPED))
                    .setMessage("Notification queued");
            receipts.forEach(receipt -> builder.addReceipts(com.sky.plugin.protocol.NotificationSendReceipt.newBuilder()
                    .setChannelId(receipt.channelId())
                    .setStatus(receipt.status().name())
                    .setMessage(receipt.message() == null ? "" : receipt.message())
                    .setHistoryId(receipt.historyId())
                    .build()));
            NotificationResponse response = builder.build();
            if (!response.getSuccess()) {
                logNotificationRejected(principal, request, response);
            }
            return response;
        } catch (IllegalArgumentException exception) {
            NotificationResponse response = NotificationResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(exception.getMessage() == null ? "Plugin notification rejected" : exception.getMessage())
                    .build();
            logNotificationRejected(principal, request, response);
            return response;
        }
    }

    private void logNotificationRejected(PluginRuntimePrincipal principal, NotificationRequest request, NotificationResponse response) {
        log.warn(
                "Plugin notification rejected: installationId={}, pluginId={}, instanceId={}, jobId={}, executionId={}, requestId={}, channelIds={}, titleBytes={}, maxTitleBytes={}, contentBytes={}, maxContentBytes={}, reason={}, receipts={}",
                safeLogValue(principal.installationId(), MAX_LOG_VALUE_CHARS),
                safeLogValue(principal.pluginId(), MAX_LOG_VALUE_CHARS),
                safeLogValue(principal.instanceId(), MAX_LOG_VALUE_CHARS),
                safeLogValue(request.getJobId(), MAX_LOG_VALUE_CHARS),
                principal.executionId(),
                safeLogValue(request.getRequestId(), MAX_LOG_VALUE_CHARS),
                channelIds(request.getChannelIdsList()),
                utf8Bytes(request.getTitle()),
                notificationSettings.maxTitleBytes(),
                utf8Bytes(request.getContent()),
                notificationSettings.maxContentBytes(),
                safeLogValue(response.getMessage(), MAX_LOG_VALUE_CHARS),
                receiptSummary(response)
        );
    }

    private String receiptSummary(NotificationResponse response) {
        StringJoiner receipts = new StringJoiner(", ", "[", "]");
        response.getReceiptsList().stream()
                .filter(receipt -> "FAILED".equals(receipt.getStatus()) || "SKIPPED".equals(receipt.getStatus()))
                .forEach(receipt -> receipts.add(
                        safeLogValue(receipt.getChannelId(), MAX_LOG_VALUE_CHARS)
                                + ":" + safeLogValue(receipt.getStatus(), MAX_LOG_VALUE_CHARS)
                                + "(message=" + safeLogValue(receipt.getMessage(), MAX_LOG_VALUE_CHARS)
                                + ", historyId=" + receipt.getHistoryId() + ")"
                ));
        return safeLogValue(receipts.toString(), MAX_RECEIPT_SUMMARY_CHARS);
    }

    private String channelIds(List<String> values) {
        StringJoiner ids = new StringJoiner(", ", "[", "]");
        values.forEach(value -> ids.add(safeLogValue(value, MAX_LOG_VALUE_CHARS)));
        return safeLogValue(ids.toString(), MAX_RECEIPT_SUMMARY_CHARS);
    }

    private int utf8Bytes(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private String safeLogValue(String value, int maxCodePoints) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder sanitized = new StringBuilder(Math.min(value.length(), maxCodePoints));
        int offset = 0;
        int count = 0;
        while (offset < value.length() && count < maxCodePoints) {
            int codePoint = value.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint)
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                sanitized.append(' ');
            } else {
                sanitized.appendCodePoint(codePoint);
            }
            offset += Character.charCount(codePoint);
            count++;
        }
        if (offset < value.length()) {
            sanitized.append("...[truncated]");
        }
        return sanitized.toString().trim();
    }

    private void verifyPluginId(PluginRuntimePrincipal principal, String requestPluginId) {
        if (requestPluginId != null && !requestPluginId.isBlank() && !principal.pluginId().equals(requestPluginId)) {
            throw Status.PERMISSION_DENIED.withDescription("Plugin identity mismatch").asRuntimeException();
        }
    }

    private void require(PluginRuntimePrincipal principal, PluginCapability capability) {
        if (!principal.hasCapability(capability)) {
            throw Status.PERMISSION_DENIED.withDescription("Plugin capability required: " + capability).asRuntimeException();
        }
    }

    private void verifyJobContext(PluginRuntimePrincipal principal, String requestJobId) {
        if (principal.taskBound()) {
            if (!Long.toString(principal.jobId()).equals(requestJobId)) {
                throw Status.PERMISSION_DENIED.withDescription("Job context mismatch").asRuntimeException();
            }
        } else if (requestJobId != null && !requestJobId.isBlank()) {
            throw Status.PERMISSION_DENIED.withDescription("Service runtime cannot claim a job context").asRuntimeException();
        }
    }
}
