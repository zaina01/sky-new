package com.sky.job.plugin.callback;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.notification.NotificationChannelStore;
import com.sky.job.notification.NotificationSendCommand;
import com.sky.job.notification.NotificationService;
import com.sky.job.notification.NotificationSourceType;
import com.sky.job.plugin.security.PluginCapability;
import com.sky.job.plugin.security.PluginRuntimePrincipal;
import com.sky.job.plugin.storage.PluginAccountStore;
import com.sky.job.plugin.storage.PluginConfigStore;
import com.sky.job.plugin.storage.PluginDataStore;
import com.sky.job.plugin.storage.PluginPayloadValidator;
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

import java.util.List;
import org.springframework.stereotype.Service;


@Service
public class PluginCallbackService {
    private final PluginConfigStore configStore;
    private final PluginAccountStore accountStore;
    private final PluginDataStore dataStore;
    private final PluginPayloadValidator payloadValidator;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final NotificationChannelStore channelStore;
    private final PluginServiceCallCoordinator serviceCallCoordinator;

    public PluginCallbackService(
            PluginConfigStore configStore,
            PluginAccountStore accountStore,
            PluginDataStore dataStore,
            PluginPayloadValidator payloadValidator,
            ObjectMapper objectMapper,
            NotificationService notificationService,
            NotificationChannelStore channelStore,
            PluginServiceCallCoordinator serviceCallCoordinator
    ) {
        this.configStore = configStore;
        this.accountStore = accountStore;
        this.dataStore = dataStore;
        this.payloadValidator = payloadValidator;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.channelStore = channelStore;
        this.serviceCallCoordinator = serviceCallCoordinator;
    }

    public void writeLog(PluginRuntimePrincipal principal, PluginLogEvent event) {
        verifyPluginId(principal, event.getPluginId());
    }

    public ConfigResponse getConfig(PluginRuntimePrincipal principal, ConfigRequest request) {
        require(principal, PluginCapability.CONFIG);
        verifyPluginId(principal, request.getPluginId());
        return ConfigResponse.newBuilder()
                .setSuccess(true)
                .setConfigJson(configStore.getConfigJson(principal.pluginId()))
                .build();
    }

    public ConfigResponse updateConfig(PluginRuntimePrincipal principal, ConfigRequest request) {
        require(principal, PluginCapability.CONFIG);
        verifyPluginId(principal, request.getPluginId());
        String configJson = payloadValidator.normalizeConfigJson(request.getConfigJson());
        configStore.upsertConfigJson(principal.pluginId(), configJson);
        return ConfigResponse.newBuilder().setSuccess(true).setConfigJson(configJson).build();
    }

    public AccountResponse listAccounts(PluginRuntimePrincipal principal, AccountRequest request) {
        require(principal, PluginCapability.ACCOUNT);
        verifyPluginId(principal, request.getPluginId());
        return AccountResponse.newBuilder()
                .setSuccess(true)
                .addAllAccounts(accountStore.listEnabled(principal.pluginId()))
                .build();
    }

    public AccountResponse getAccount(PluginRuntimePrincipal principal, AccountRequest request) {
        require(principal, PluginCapability.ACCOUNT);
        verifyPluginId(principal, request.getPluginId());
        payloadValidator.validateAccountId(request.getAccountId());
        return accountStore.getEnabled(principal.pluginId(), request.getAccountId())
                .map(account -> AccountResponse.newBuilder().setSuccess(true).addAccounts(account).build())
                .orElseGet(() -> AccountResponse.newBuilder().setSuccess(false).setMessage("Account not found").build());
    }

    public AccountResponse updateAccount(PluginRuntimePrincipal principal, AccountRequest request) {
        require(principal, PluginCapability.ACCOUNT);
        verifyPluginId(principal, request.getPluginId());
        payloadValidator.validateAccountId(request.getAccountId());
        String accountJson = payloadValidator.normalizeAccountJson(request.getAccountJson());
        accountStore.upsert(principal.pluginId(), request.getAccountId(), accountJson);
        return AccountResponse.newBuilder().setSuccess(true).build();
    }

    public PluginDataResponse getData(PluginRuntimePrincipal principal, PluginDataRequest request) {
        verifyPluginId(principal, request.getPluginId());
        payloadValidator.validateCollection(request.getCollection());
        payloadValidator.validateKey(request.getKey());
        return dataStore.select(principal.pluginId(), request.getCollection(), request.getKey())
                .map(dataJson -> PluginDataResponse.newBuilder().setSuccess(true).setDataJson(dataJson).build())
                .orElseGet(() -> PluginDataResponse.newBuilder().setSuccess(false).setMessage("Data not found").build());
    }

    public PluginDataResponse putData(PluginRuntimePrincipal principal, PluginDataRequest request) {
        verifyPluginId(principal, request.getPluginId());
        payloadValidator.validateCollection(request.getCollection());
        payloadValidator.validateKey(request.getKey());
        String dataJson = payloadValidator.normalizeDataJson(request.getDataJson());
        dataStore.upsert(principal.pluginId(), request.getCollection(), request.getKey(), dataJson);
        return PluginDataResponse.newBuilder().setSuccess(true).setDataJson(dataJson).build();
    }

    public PluginDataResponse deleteData(PluginRuntimePrincipal principal, PluginDataRequest request) {
        verifyPluginId(principal, request.getPluginId());
        payloadValidator.validateCollection(request.getCollection());
        payloadValidator.validateKey(request.getKey());
        boolean deleted = dataStore.delete(principal.pluginId(), request.getCollection(), request.getKey());
        return PluginDataResponse.newBuilder().setSuccess(true).setDeleted(deleted).build();
    }

    public PluginDataResponse listData(PluginRuntimePrincipal principal, PluginDataRequest request) {
        verifyPluginId(principal, request.getPluginId());
        payloadValidator.validateCollection(request.getCollection());
        PluginDataResponse.Builder builder = PluginDataResponse.newBuilder().setSuccess(true);
        try {
            JsonNode root = objectMapper.readTree(dataStore.selectAll(principal.pluginId(), request.getCollection()));
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
        channelStore.listEnabled().forEach(channel -> builder.addChannels(NotificationChannelInfo.newBuilder()
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
        try {
            List<com.sky.job.notification.NotificationSendReceipt> receipts = notificationService.sendFromPlugin(principal, new NotificationSendCommand(
                    NotificationSourceType.PLUGIN_RUNTIME,
                    request.getRequestId(),
                    principal.pluginId(),
                    principal.instanceId(),
                    request.getJobId(),
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
            return builder.build();
        } catch (IllegalArgumentException exception) {
            return NotificationResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(exception.getMessage() == null ? "Plugin notification rejected" : exception.getMessage())
                    .build();
        }
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
}
