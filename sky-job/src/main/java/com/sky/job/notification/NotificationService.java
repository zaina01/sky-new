package com.sky.job.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.audit.SystemLogStore;
import com.sky.job.notification.dto.CreateNotificationChannelRequest;
import com.sky.job.notification.dto.CreateNotificationRuleRequest;
import com.sky.job.notification.dto.UpdateNotificationChannelRequest;
import com.sky.job.notification.dto.UpdateNotificationRuleRequest;
import com.sky.job.notification.provider.NotificationProviderRegistry;
import com.sky.job.plugin.security.PluginRuntimePrincipal;
import com.sky.job.plugin.storage.PluginAuditStore;
import com.sky.job.system.DynamicNotificationSettings;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final DynamicNotificationSettings properties;
    private final NotificationChannelStore channelStore;
    private final NotificationRuleStore ruleStore;
    private final NotificationSendHistoryStore historyStore;
    private final NotificationProviderRegistry providerRegistry;
    private final NotificationPayloadValidator validator;
    private final SystemLogStore systemLogStore;
    private final PluginAuditStore pluginAuditStore;
    private final ObjectMapper objectMapper;

    public NotificationService(
            DynamicNotificationSettings properties,
            NotificationChannelStore channelStore,
            NotificationRuleStore ruleStore,
            NotificationSendHistoryStore historyStore,
            NotificationProviderRegistry providerRegistry,
            NotificationPayloadValidator validator,
            SystemLogStore systemLogStore,
            PluginAuditStore pluginAuditStore,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.channelStore = channelStore;
        this.ruleStore = ruleStore;
        this.historyStore = historyStore;
        this.providerRegistry = providerRegistry;
        this.validator = validator;
        this.systemLogStore = systemLogStore;
        this.pluginAuditStore = pluginAuditStore;
        this.objectMapper = objectMapper;
    }

    public void createChannel(CreateNotificationChannelRequest request, String actor, HttpServletRequest httpRequest) {
        String channelId = validator.validateChannelId(request.channelId());
        NotificationChannelType type = requireType(request.channelType());
        String configJson = configJson(request.configJson());
        providerRegistry.get(type).validateConfig(configJson);
        channelStore.insert(channelId, type, value(request.displayName()), request.enabled() == null || request.enabled(), configJson, value(request.remark()), value(actor));
        systemLogStore.record("NOTIFICATION", "CREATE_NOTIFICATION_CHANNEL", "INFO", "SUCCESS", "NOTIFICATION_CHANNEL", channelId, "Notification channel created: type=" + type, httpRequest);
    }

    public void updateChannel(String channelId, UpdateNotificationChannelRequest request, String actor, HttpServletRequest httpRequest) {
        channelId = validator.validateChannelId(channelId);
        NotificationChannel existing = channelStore.findByChannelId(channelId).orElseThrow(() -> new IllegalArgumentException("Notification channel not found"));
        NotificationChannelType type = request.channelType() == null ? existing.channelType() : request.channelType();
        String configJson = request.configJson() == null || request.configJson().isBlank() ? existing.configJson() : request.configJson();
        providerRegistry.get(type).validateConfig(configJson);
        int updated = channelStore.update(channelId, type, request.displayName() == null ? existing.displayName() : request.displayName(), request.enabled() == null ? existing.enabled() : request.enabled(), configJson, request.remark() == null ? existing.remark() : request.remark(), value(actor));
        if (updated == 0) {
            throw new IllegalArgumentException("Notification channel not found");
        }
        systemLogStore.record("NOTIFICATION", "UPDATE_NOTIFICATION_CHANNEL", "INFO", "SUCCESS", "NOTIFICATION_CHANNEL", channelId, "Notification channel updated: type=" + type, httpRequest);
    }

    public void updateEnabled(String channelId, boolean enabled, String actor, HttpServletRequest httpRequest) {
        channelId = validator.validateChannelId(channelId);
        if (channelStore.updateEnabled(channelId, enabled, value(actor)) == 0) {
            throw new IllegalArgumentException("Notification channel not found");
        }
        systemLogStore.record("NOTIFICATION", "UPDATE_NOTIFICATION_CHANNEL_STATE", "INFO", "SUCCESS", "NOTIFICATION_CHANNEL", channelId, "Notification channel enabled=" + enabled, httpRequest);
    }

    public void delete(String channelId, HttpServletRequest httpRequest) {
        channelId = validator.validateChannelId(channelId);
        if (channelStore.delete(channelId) == 0) {
            throw new IllegalArgumentException("Notification channel not found");
        }
        systemLogStore.record("NOTIFICATION", "DELETE_NOTIFICATION_CHANNEL", "INFO", "SUCCESS", "NOTIFICATION_CHANNEL", channelId, "Notification channel deleted", httpRequest);
    }

    public void createRule(CreateNotificationRuleRequest request, String actor, HttpServletRequest httpRequest) {
        String ruleId = validator.validateRuleId(request.ruleId());
        NotificationEventType eventType = requireEventType(request.eventType());
        List<String> channelIds = validator.validateRuleChannelIds(request.channelIds());
        String severity = validator.normalizeOptionalSeverity(request.severity());
        ruleStore.insert(ruleId, value(request.displayName()), request.enabled() == null || request.enabled(), eventType, validator.normalizeOptionalPluginId(request.pluginId()), validator.normalizeOptionalJobId(request.jobId()), severity, channelIds, value(request.remark()), value(actor));
        systemLogStore.record("NOTIFICATION", "CREATE_NOTIFICATION_RULE", "INFO", "SUCCESS", "NOTIFICATION_RULE", ruleId, "Notification rule created: eventType=" + eventType + ", channels=" + channelIds.size(), httpRequest);
    }

    public void updateRule(String ruleId, UpdateNotificationRuleRequest request, String actor, HttpServletRequest httpRequest) {
        ruleId = validator.validateRuleId(ruleId);
        NotificationRule existing = ruleStore.findByRuleId(ruleId).orElseThrow(() -> new IllegalArgumentException("Notification rule not found"));
        NotificationEventType eventType = request.eventType() == null ? existing.eventType() : request.eventType();
        List<String> channelIds = request.channelIds() == null ? existing.channelIds() : validator.validateRuleChannelIds(request.channelIds());
        int updated = ruleStore.update(
                ruleId,
                request.displayName() == null ? existing.displayName() : request.displayName(),
                request.enabled() == null ? existing.enabled() : request.enabled(),
                eventType,
                request.pluginId() == null ? existing.pluginId() : validator.normalizeOptionalPluginId(request.pluginId()),
                request.jobId() == null ? existing.jobId() : validator.normalizeOptionalJobId(request.jobId()),
                request.severity() == null ? existing.severity() : validator.normalizeOptionalSeverity(request.severity()),
                channelIds,
                request.remark() == null ? existing.remark() : request.remark(),
                value(actor)
        );
        if (updated == 0) {
            throw new IllegalArgumentException("Notification rule not found");
        }
        systemLogStore.record("NOTIFICATION", "UPDATE_NOTIFICATION_RULE", "INFO", "SUCCESS", "NOTIFICATION_RULE", ruleId, "Notification rule updated: eventType=" + eventType + ", channels=" + channelIds.size(), httpRequest);
    }

    public void updateRuleEnabled(String ruleId, boolean enabled, String actor, HttpServletRequest httpRequest) {
        ruleId = validator.validateRuleId(ruleId);
        if (ruleStore.updateEnabled(ruleId, enabled, value(actor)) == 0) {
            throw new IllegalArgumentException("Notification rule not found");
        }
        systemLogStore.record("NOTIFICATION", "UPDATE_NOTIFICATION_RULE_STATE", "INFO", "SUCCESS", "NOTIFICATION_RULE", ruleId, "Notification rule enabled=" + enabled, httpRequest);
    }

    public void deleteRule(String ruleId, HttpServletRequest httpRequest) {
        ruleId = validator.validateRuleId(ruleId);
        if (ruleStore.delete(ruleId) == 0) {
            throw new IllegalArgumentException("Notification rule not found");
        }
        systemLogStore.record("NOTIFICATION", "DELETE_NOTIFICATION_RULE", "INFO", "SUCCESS", "NOTIFICATION_RULE", ruleId, "Notification rule deleted", httpRequest);
    }

    public List<NotificationSendReceipt> testSend(String channelId, String title, String content, String severity, HttpServletRequest httpRequest) {
        NotificationSendCommand command = new NotificationSendCommand(
                NotificationSourceType.ADMIN_TEST,
                UUID.randomUUID().toString(),
                "",
                "",
                "",
                null,
                List.of(validator.validateChannelId(channelId)),
                title,
                content,
                severity,
                Map.of()
        );
        List<NotificationSendReceipt> receipts = send(command);
        systemLogStore.record("NOTIFICATION", "TEST_NOTIFICATION_CHANNEL", "INFO", "SUCCESS", "NOTIFICATION_CHANNEL", channelId, "Notification test queued: receipts=" + receipts.size(), httpRequest);
        return receipts;
    }

    public void manualRetry(long id, String actor, HttpServletRequest httpRequest) {
        NotificationSendHistory history = historyStore.findById(id).orElseThrow(() -> new IllegalArgumentException("Notification send history not found"));
        if (history.contentPayload() == null || history.contentPayload().isBlank()) {
            throw new IllegalStateException("Notification payload is not retryable");
        }
        if (historyStore.requestManualRetry(id, value(actor), java.time.LocalDateTime.now()) == 0) {
            throw new IllegalStateException("Notification send history is not failed or retryable");
        }
        systemLogStore.record("NOTIFICATION", "RETRY_NOTIFICATION_SEND", "INFO", "SUCCESS", "NOTIFICATION_SEND_HISTORY", Long.toString(id), "Notification retry queued: channelId=" + history.channelId(), httpRequest);
    }

    public List<NotificationSendReceipt> sendFromPlugin(PluginRuntimePrincipal principal, NotificationSendCommand command) {
        List<NotificationSendReceipt> receipts = send(command);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("requestId", command.requestId());
        detail.put("jobId", command.jobId());
        detail.put("channelIds", command.channelIds());
        detail.put("title", command.title());
        detail.put("payloadBytes", validator.bytes(command.content()));
        detail.put("receipts", receipts.stream().map(receipt -> Map.of("channelId", receipt.channelId(), "status", receipt.status().name(), "historyId", receipt.historyId())).toList());
        pluginAuditStore.insert(principal.pluginId(), principal.instanceId(), principal.pluginId(), "PLUGIN_NOTIFICATION_SEND", json(detail));
        return receipts;
    }

    public void sendHostAlert(HostNotificationEvent event) {
        try {
            String severity = validator.normalizeOptionalSeverity(event.severity());
            List<String> channelIds = resolveHostAlertChannels(event, severity);
            if (channelIds.isEmpty()) {
                return;
            }
            send(new NotificationSendCommand(
                    NotificationSourceType.HOST_ALERT,
                    UUID.randomUUID().toString(),
                    value(event.pluginId()),
                    value(event.instanceId()),
                    value(event.jobId()),
                    event.executionId(),
                    channelIds,
                    event.title(),
                    event.content(),
                    severity,
                    event.labels()
            ));
        } catch (Exception exception) {
            log.warn("Failed to send host notification alert: eventType={}, pluginId={}, jobId={}, executionId={}, errorType={}", event.eventType(), event.pluginId(), event.jobId(), event.executionId(), exception.getClass().getSimpleName());
        }
    }

    private List<String> resolveHostAlertChannels(HostNotificationEvent event, String severity) {
        List<NotificationRule> rules = ruleStore.listEnabledCandidates(event.eventType(), value(event.pluginId()), value(event.jobId()), severity);
        Map<String, Boolean> channelIds = new LinkedHashMap<>();
        for (NotificationRule rule : rules) {
            if (matches(rule, event, severity)) {
                for (String channelId : rule.channelIds()) {
                    channelIds.put(channelId, true);
                }
            }
        }
        return new ArrayList<>(channelIds.keySet());
    }

    private boolean matches(NotificationRule rule, HostNotificationEvent event, String severity) {
        return rule.enabled()
                && rule.eventType() == event.eventType()
                && (rule.pluginId().isBlank() || rule.pluginId().equals(value(event.pluginId())))
                && (rule.jobId().isBlank() || rule.jobId().equals(value(event.jobId())))
                && (rule.severity().isBlank() || rule.severity().equals(severity));
    }

    public List<NotificationSendReceipt> send(NotificationSendCommand command) {
        String title = validator.validateTitle(command.title());
        String content = validator.validateContent(command.content());
        String severity = validator.severity(command.severity());
        List<String> channelIds = validator.validateChannelIds(command.channelIds());
        List<NotificationChannel> channels = channelStore.listEnabledByIds(channelIds);
        Map<String, NotificationChannel> channelById = new LinkedHashMap<>();
        channels.forEach(channel -> channelById.put(channel.channelId(), channel));
        List<NotificationSendReceipt> receipts = new ArrayList<>();
        for (String channelId : channelIds) {
            NotificationChannel channel = channelById.get(channelId);
            if (channel == null) {
                receipts.add(new NotificationSendReceipt(channelId, NotificationSendStatus.SKIPPED, "Notification channel not found or disabled", 0));
                continue;
            }
            receipts.add(sendToChannel(command, channel, title, content, severity));
        }
        return receipts;
    }

    private NotificationSendReceipt sendToChannel(NotificationSendCommand originalCommand, NotificationChannel channel, String title, String content, String severity) {
        NotificationSendCommand command = new NotificationSendCommand(originalCommand.sourceType(), originalCommand.requestId(), originalCommand.pluginId(), originalCommand.instanceId(), originalCommand.jobId(), originalCommand.executionId(), List.of(channel.channelId()), title, content, severity, originalCommand.labels());
        long historyId = historyStore.insertPending(command, channel, title, content, validator.preview(content), validator.bytes(content), json(command.labels()), properties.retryMaxAttempts());
        return new NotificationSendReceipt(channel.channelId(), NotificationSendStatus.PENDING, "Notification queued", historyId);
    }

    public void withSafeHostAlert(Consumer<NotificationService> callback) {
        try {
            callback.accept(this);
        } catch (Exception exception) {
            log.warn("Failed to run host notification callback: errorType={}", exception.getClass().getSimpleName());
        }
    }

    private NotificationSendResult sanitize(NotificationSendResult result) {
        return new NotificationSendResult(
                result.success(),
                validator.sanitizeMessage(result.providerStatusCode()),
                validator.sanitizeMessage(result.providerMessage()),
                validator.sanitizeMessage(result.errorType()),
                validator.sanitizeMessage(result.errorMessage())
        );
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private NotificationEventType requireEventType(NotificationEventType eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("Notification rule eventType is required");
        }
        return eventType;
    }

    private NotificationChannelType requireType(NotificationChannelType type) {
        if (type == null) {
            throw new IllegalArgumentException("Notification channel type is required");
        }
        return type;
    }

    private String configJson(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            throw new IllegalArgumentException("Notification channel config is required");
        }
        return configJson;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
