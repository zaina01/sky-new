package com.sky.job.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.audit.SystemLogStore;
import com.sky.job.notification.dto.CreateNotificationChannelRequest;
import com.sky.job.notification.dto.CreateNotificationRuleRequest;
import com.sky.job.notification.dto.UpdateNotificationChannelRequest;
import com.sky.job.notification.dto.UpdateNotificationRuleRequest;
import com.sky.job.notification.provider.NotificationDeliveryPart;
import com.sky.job.notification.provider.NotificationProviderRegistry;
import com.sky.job.plugin.security.PluginRuntimePrincipal;
import com.sky.job.plugin.storage.PluginAuditStore;
import com.sky.job.system.DynamicNotificationSettings;
import com.sky.job.scheduler.JobStore;
import com.sky.job.scheduler.JobExecutionStore;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import io.grpc.Status;
import com.sky.job.security.AuthenticatedUser;
import com.sky.job.security.DataScopeService;
import com.sky.job.security.EffectiveDataScope;
import com.sky.job.security.PermissionNames;
import com.sky.job.web.ResourceNotFoundException;
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
    private final PluginRuntimeMetadataStore runtimeStore;
    private final JobStore jobStore;
    private final JobExecutionStore executionStore;
    private final DataScopeService dataScopeService;

    public NotificationService(
            DynamicNotificationSettings properties,
            NotificationChannelStore channelStore,
            NotificationRuleStore ruleStore,
            NotificationSendHistoryStore historyStore,
            NotificationProviderRegistry providerRegistry,
            NotificationPayloadValidator validator,
            SystemLogStore systemLogStore,
            PluginAuditStore pluginAuditStore,
            ObjectMapper objectMapper,
            PluginRuntimeMetadataStore runtimeStore,
            JobStore jobStore,
            JobExecutionStore executionStore,
            DataScopeService dataScopeService
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
        this.runtimeStore = runtimeStore;
        this.jobStore = jobStore;
        this.executionStore = executionStore;
        this.dataScopeService = dataScopeService;
    }

    public void createChannel(CreateNotificationChannelRequest request, AuthenticatedUser user, HttpServletRequest httpRequest) {
        String channelId = validator.validateChannelId(request.channelId());
        NotificationChannelType type = requireType(request.channelType());
        String configJson = configJson(request.configJson());
        providerRegistry.get(type).validateConfig(configJson);
        if (user.departmentId() == null) throw new IllegalStateException("Current user has no department");
        channelStore.insert(channelId, type, value(request.displayName()), request.enabled() == null || request.enabled(), configJson, value(request.remark()), user.userId(), user.departmentId(), user.username());
        systemLogStore.record("NOTIFICATION", "CREATE_NOTIFICATION_CHANNEL", "INFO", "SUCCESS", "NOTIFICATION_CHANNEL", channelId, "Notification channel created: type=" + type, httpRequest);
    }

    public void updateChannel(String channelId, UpdateNotificationChannelRequest request, AuthenticatedUser user, HttpServletRequest httpRequest) {
        channelId = validator.validateChannelId(channelId);
        NotificationChannel existing = requireChannel(channelId, user, PermissionNames.NOTIFICATION_CHANNEL_WRITE);
        NotificationChannelType type = request.channelType() == null ? existing.channelType() : request.channelType();
        String configJson = request.configJson() == null || request.configJson().isBlank() ? existing.configJson() : request.configJson();
        providerRegistry.get(type).validateConfig(configJson);
        int updated = channelStore.update(channelId, type, request.displayName() == null ? existing.displayName() : request.displayName(), request.enabled() == null ? existing.enabled() : request.enabled(), configJson, request.remark() == null ? existing.remark() : request.remark(), user.username());
        if (updated == 0) {
            throw new ResourceNotFoundException("Notification channel not found");
        }
        systemLogStore.record("NOTIFICATION", "UPDATE_NOTIFICATION_CHANNEL", "INFO", "SUCCESS", "NOTIFICATION_CHANNEL", channelId, "Notification channel updated: type=" + type, httpRequest);
    }

    public void updateEnabled(String channelId, boolean enabled, AuthenticatedUser user, HttpServletRequest httpRequest) {
        channelId = validator.validateChannelId(channelId);
        requireChannel(channelId, user, PermissionNames.NOTIFICATION_CHANNEL_WRITE);
        if (channelStore.updateEnabled(channelId, enabled, user.username()) == 0) {
            throw new ResourceNotFoundException("Notification channel not found");
        }
        systemLogStore.record("NOTIFICATION", "UPDATE_NOTIFICATION_CHANNEL_STATE", "INFO", "SUCCESS", "NOTIFICATION_CHANNEL", channelId, "Notification channel enabled=" + enabled, httpRequest);
    }

    public void delete(String channelId, AuthenticatedUser user, HttpServletRequest httpRequest) {
        channelId = validator.validateChannelId(channelId);
        requireChannel(channelId, user, PermissionNames.NOTIFICATION_CHANNEL_WRITE);
        if (channelStore.hasBindings(channelId)) throw new IllegalStateException("Notification channel is still bound to a plugin installation, job, or rule");
        if (channelStore.delete(channelId) == 0) {
            throw new ResourceNotFoundException("Notification channel not found");
        }
        systemLogStore.record("NOTIFICATION", "DELETE_NOTIFICATION_CHANNEL", "INFO", "SUCCESS", "NOTIFICATION_CHANNEL", channelId, "Notification channel deleted", httpRequest);
    }

    public void createRule(CreateNotificationRuleRequest request, AuthenticatedUser user, HttpServletRequest httpRequest) {
        String ruleId = validator.validateRuleId(request.ruleId());
        NotificationEventType eventType = requireEventType(request.eventType());
        List<String> channelIds = validator.validateRuleChannelIds(request.channelIds());
        String severity = validator.normalizeOptionalSeverity(request.severity());
        RuleTarget target = validateRuleResources(request.pluginInstallationId(), request.jobId(), channelIds, user);
        if (user.departmentId() == null) throw new IllegalStateException("Current user has no department");
        ruleStore.insert(ruleId, value(request.displayName()), request.enabled() == null || request.enabled(), eventType, target.installationId(), target.jobId(), severity, channelIds, value(request.remark()), user.userId(), user.departmentId(), user.username());
        systemLogStore.record("NOTIFICATION", "CREATE_NOTIFICATION_RULE", "INFO", "SUCCESS", "NOTIFICATION_RULE", ruleId, "Notification rule created: eventType=" + eventType + ", channels=" + channelIds.size(), httpRequest);
    }

    public void updateRule(String ruleId, UpdateNotificationRuleRequest request, AuthenticatedUser user, HttpServletRequest httpRequest) {
        ruleId = validator.validateRuleId(ruleId);
        NotificationRule existing = requireRule(ruleId, user, PermissionNames.NOTIFICATION_RULE_WRITE);
        NotificationEventType eventType = request.eventType() == null ? existing.eventType() : request.eventType();
        List<String> channelIds = request.channelIds() == null ? existing.channelIds() : validator.validateRuleChannelIds(request.channelIds());
        RuleTarget target = validateRuleResources(
                request.pluginInstallationId() == null ? existing.pluginInstallationId() : request.pluginInstallationId(),
                request.jobId() == null ? existing.jobId() : request.jobId(),
                channelIds,
                user
        );
        int updated = ruleStore.update(
                ruleId,
                request.displayName() == null ? existing.displayName() : request.displayName(),
                request.enabled() == null ? existing.enabled() : request.enabled(),
                eventType,
                target.installationId(),
                target.jobId(),
                request.severity() == null ? existing.severity() : validator.normalizeOptionalSeverity(request.severity()),
                channelIds,
                request.remark() == null ? existing.remark() : request.remark(),
                user.username()
        );
        if (updated == 0) {
            throw new ResourceNotFoundException("Notification rule not found");
        }
        systemLogStore.record("NOTIFICATION", "UPDATE_NOTIFICATION_RULE", "INFO", "SUCCESS", "NOTIFICATION_RULE", ruleId, "Notification rule updated: eventType=" + eventType + ", channels=" + channelIds.size(), httpRequest);
    }

    public void updateRuleEnabled(String ruleId, boolean enabled, AuthenticatedUser user, HttpServletRequest httpRequest) {
        ruleId = validator.validateRuleId(ruleId);
        requireRule(ruleId, user, PermissionNames.NOTIFICATION_RULE_WRITE);
        if (ruleStore.updateEnabled(ruleId, enabled, user.username()) == 0) {
            throw new ResourceNotFoundException("Notification rule not found");
        }
        systemLogStore.record("NOTIFICATION", "UPDATE_NOTIFICATION_RULE_STATE", "INFO", "SUCCESS", "NOTIFICATION_RULE", ruleId, "Notification rule enabled=" + enabled, httpRequest);
    }

    public void deleteRule(String ruleId, AuthenticatedUser user, HttpServletRequest httpRequest) {
        ruleId = validator.validateRuleId(ruleId);
        requireRule(ruleId, user, PermissionNames.NOTIFICATION_RULE_WRITE);
        if (ruleStore.delete(ruleId) == 0) {
            throw new ResourceNotFoundException("Notification rule not found");
        }
        systemLogStore.record("NOTIFICATION", "DELETE_NOTIFICATION_RULE", "INFO", "SUCCESS", "NOTIFICATION_RULE", ruleId, "Notification rule deleted", httpRequest);
    }

    public List<NotificationSendReceipt> testSend(String channelId, String title, String content, String severity, AuthenticatedUser user, HttpServletRequest httpRequest) {
        requireChannel(validator.validateChannelId(channelId), user, PermissionNames.NOTIFICATION_CHANNEL_TEST);
        NotificationSendCommand command = new NotificationSendCommand(
                NotificationSourceType.ADMIN_TEST,
                UUID.randomUUID().toString(),
                "",
                "",
                "",
                "",
                null,
                user.userId(),
                user.departmentId(),
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

    public void manualRetry(long id, AuthenticatedUser user, HttpServletRequest httpRequest) {
        EffectiveDataScope retryScope = dataScopeService.resolve(user, PermissionNames.NOTIFICATION_HISTORY_RETRY);
        NotificationSendHistory history = historyStore.findById(id, retryScope).orElseThrow(() -> new ResourceNotFoundException("Notification send history not found"));
        NotificationChannel currentChannel = channelStore.findByChannelIdUnscoped(history.channelId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification channel not found"));
        if (!dataScopeService.allows(retryScope, currentChannel.ownerUserId(), currentChannel.ownerDepartmentId())) {
            throw new ResourceNotFoundException("Notification send history not found");
        }
        if (history.contentPayload() == null || history.contentPayload().isBlank()) {
            throw new IllegalStateException("Notification payload is not retryable");
        }
        if (historyStore.requestManualRetry(id, user.username(), java.time.LocalDateTime.now()) == 0) {
            throw new IllegalStateException("Notification send history is not failed or retryable");
        }
        systemLogStore.record("NOTIFICATION", "RETRY_NOTIFICATION_SEND", "INFO", "SUCCESS", "NOTIFICATION_SEND_HISTORY", Long.toString(id), "Notification retry queued: channelId=" + history.channelId(), httpRequest);
    }

    public List<NotificationSendReceipt> sendFromPlugin(PluginRuntimePrincipal principal, NotificationSendCommand command) {
        if (!principal.installationId().equals(command.installationId())) {
            throw Status.PERMISSION_DENIED.withDescription("Plugin installation mismatch").asRuntimeException();
        }
        OwnerSnapshot sourceOwner = runtimeSourceOwner(principal);
        java.util.Set<String> allowed = authorizedChannelIds(principal);
        if (command.channelIds().stream().anyMatch(channelId -> !allowed.contains(channelId))) {
            throw Status.PERMISSION_DENIED.withDescription("Notification channel is outside the runtime whitelist").asRuntimeException();
        }
        NotificationSendCommand ownedCommand = withSourceOwner(command, sourceOwner);
        List<NotificationSendReceipt> receipts = send(ownedCommand);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("requestId", command.requestId());
        detail.put("jobId", command.jobId());
        detail.put("channelIds", command.channelIds());
        detail.put("title", command.title());
        detail.put("payloadBytes", validator.bytes(command.content()));
        detail.put("receipts", receipts.stream().map(receipt -> Map.of("channelId", receipt.channelId(), "status", receipt.status().name(), "historyId", receipt.historyId())).toList());
        pluginAuditStore.insert(principal.installationId(), principal.pluginId(), principal.instanceId(), principal.pluginId(), "PLUGIN_NOTIFICATION_SEND", json(detail));
        return receipts;
    }

    public List<NotificationChannel> listAuthorizedChannels(PluginRuntimePrincipal principal) {
        return channelStore.listByIdsUnscoped(new ArrayList<>(authorizedChannelIds(principal)));
    }

    public void sendHostAlert(HostNotificationEvent event) {
        try {
            String severity = validator.normalizeOptionalSeverity(event.severity());
            List<String> channelIds = resolveHostAlertChannels(event, severity);
            if (channelIds.isEmpty()) {
                return;
            }
            OwnerSnapshot sourceOwner = hostSourceOwner(event);
            send(new NotificationSendCommand(
                    NotificationSourceType.HOST_ALERT,
                    UUID.randomUUID().toString(),
                    value(event.installationId()),
                    value(event.pluginId()),
                    value(event.instanceId()),
                    value(event.jobId()),
                    event.executionId(),
                    sourceOwner.userId(),
                    sourceOwner.departmentId(),
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
        List<NotificationRule> rules = ruleStore.listEnabledCandidates(event.eventType(), value(event.installationId()), value(event.jobId()), severity);
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
                && (rule.pluginInstallationId().isBlank() || rule.pluginInstallationId().equals(value(event.installationId())))
                && (rule.jobId().isBlank() || rule.jobId().equals(value(event.jobId())))
                && (rule.severity().isBlank() || rule.severity().equals(severity));
    }

    public List<NotificationSendReceipt> send(NotificationSendCommand command) {
        String title = validator.validateTitle(command.title());
        String content = validator.validateContent(command.content());
        String severity = validator.severity(command.severity());
        List<String> channelIds = validator.validateChannelIds(command.channelIds());
        List<NotificationChannel> channels = channelStore.listByIdsUnscoped(channelIds);
        Map<String, NotificationChannel> channelById = new LinkedHashMap<>();
        channels.forEach(channel -> channelById.put(channel.channelId(), channel));
        Map<String, List<NotificationDeliveryPart>> deliveryPartsByChannel = new LinkedHashMap<>();
        for (String channelId : channelIds) {
            NotificationChannel channel = channelById.get(channelId);
            if (channel == null || !channel.enabled()) {
                continue;
            }
            NotificationSendCommand channelCommand = channelCommand(command, channel, title, content, severity);
            List<NotificationDeliveryPart> parts = providerRegistry.get(channel.channelType()).plan(channel, channelCommand);
            validateDeliveryParts(channel, parts);
            deliveryPartsByChannel.put(channelId, List.copyOf(parts));
        }

        List<NotificationSendReceipt> receipts = new ArrayList<>();
        for (String channelId : channelIds) {
            NotificationChannel channel = channelById.get(channelId);
            if (channel == null) {
                receipts.add(new NotificationSendReceipt(channelId, NotificationSendStatus.SKIPPED, "Notification channel not found", 0));
                continue;
            }
            if (!channel.enabled()) {
                String message = "Notification channel is disabled";
                long historyId = historyStore.insertPending(command, channel, title, content, validator.preview(content), validator.bytes(content), json(command.labels()), properties.retryMaxAttempts());
                historyStore.markSkipped(historyId, message);
                receipts.add(new NotificationSendReceipt(channelId, NotificationSendStatus.SKIPPED, message, historyId));
                continue;
            }
            receipts.addAll(sendToChannel(command, channel, severity, deliveryPartsByChannel.get(channelId)));
        }
        return receipts;
    }

    private List<NotificationSendReceipt> sendToChannel(NotificationSendCommand originalCommand, NotificationChannel channel, String severity, List<NotificationDeliveryPart> parts) {
        List<NotificationSendReceipt> receipts = new ArrayList<>(parts.size());
        String labelsJson = json(originalCommand.labels());
        for (NotificationDeliveryPart part : parts) {
            NotificationSendCommand command = channelCommand(originalCommand, channel, part.title(), part.content(), severity);
            long historyId = historyStore.insertPending(command, channel, part.title(), part.content(), validator.preview(part.content()), validator.bytes(part.content()), labelsJson, properties.retryMaxAttempts());
            String message = part.multipart()
                    ? "Notification chunk " + part.partIndex() + "/" + part.partCount() + " queued"
                    : "Notification queued";
            receipts.add(new NotificationSendReceipt(channel.channelId(), NotificationSendStatus.PENDING, message, historyId));
        }
        return receipts;
    }

    private NotificationSendCommand channelCommand(NotificationSendCommand originalCommand, NotificationChannel channel, String title, String content, String severity) {
        return new NotificationSendCommand(
                originalCommand.sourceType(), originalCommand.requestId(), originalCommand.installationId(),
                originalCommand.pluginId(), originalCommand.instanceId(), originalCommand.jobId(),
                originalCommand.executionId(), originalCommand.sourceOwnerUserId(),
                originalCommand.sourceOwnerDepartmentId(), List.of(channel.channelId()), title, content,
                severity, originalCommand.labels());
    }

    private void validateDeliveryParts(NotificationChannel channel, List<NotificationDeliveryPart> parts) {
        if (parts == null || parts.isEmpty()) {
            throw new IllegalStateException("Notification provider returned no delivery parts: " + channel.channelType());
        }
        for (int index = 0; index < parts.size(); index++) {
            NotificationDeliveryPart part = parts.get(index);
            if (part == null || part.partIndex() != index + 1 || part.partCount() != parts.size()) {
                throw new IllegalStateException("Notification provider returned an invalid delivery plan: " + channel.channelType());
            }
        }
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

    private NotificationChannel requireChannel(String channelId, AuthenticatedUser user, String permissionName) {
        EffectiveDataScope scope = dataScopeService.resolve(user, permissionName);
        return channelStore.findByChannelId(channelId, scope).orElseThrow(() -> new ResourceNotFoundException("Notification channel not found"));
    }

    private NotificationRule requireRule(String ruleId, AuthenticatedUser user, String permissionName) {
        return ruleStore.findByRuleId(ruleId, dataScopeService.resolve(user, permissionName))
                .orElseThrow(() -> new ResourceNotFoundException("Notification rule not found"));
    }

    private RuleTarget validateRuleResources(String installationId, String jobId, List<String> channelIds, AuthenticatedUser user) {
        String normalizedInstallationId = optional(installationId);
        String normalizedJobId = validator.normalizeOptionalJobId(jobId);
        EffectiveDataScope writeScope = dataScopeService.resolve(user, PermissionNames.NOTIFICATION_RULE_WRITE);
        if (!normalizedJobId.isBlank()) {
            long id;
            try {
                id = Long.parseLong(normalizedJobId);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid notification rule job id");
            }
            com.sky.job.scheduler.ScheduledJob job = jobStore.findById(id, writeScope)
                    .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
            if (normalizedInstallationId.isBlank()) normalizedInstallationId = job.pluginInstallationId();
            if (!normalizedInstallationId.equals(job.pluginInstallationId())) {
                throw new IllegalArgumentException("Notification rule job does not belong to the selected plugin installation");
            }
        }
        if (!normalizedInstallationId.isBlank()) {
            var runtime = runtimeStore.findRuntimeByInstallationId(normalizedInstallationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Plugin installation not found"));
            if (!dataScopeService.allows(writeScope, runtime.ownerUserId(), runtime.ownerDepartmentId())) {
                throw new ResourceNotFoundException("Plugin installation not found");
            }
        }
        requirePermission(user, PermissionNames.NOTIFICATION_CHANNEL_READ);
        EffectiveDataScope channelScope = dataScopeService.resolve(user, PermissionNames.NOTIFICATION_CHANNEL_READ);
        for (String channelId : channelIds) {
            channelStore.findByChannelId(channelId, channelScope)
                    .orElseThrow(() -> new ResourceNotFoundException("Notification channel not found"));
        }
        return new RuleTarget(normalizedInstallationId, normalizedJobId);
    }

    private void requirePermission(AuthenticatedUser user, String permission) {
        if (!user.roles().contains("ADMIN") && !user.permissions().contains(permission)) {
            throw new org.springframework.security.access.AccessDeniedException("Missing permission: " + permission);
        }
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private java.util.Set<String> authorizedChannelIds(PluginRuntimePrincipal principal) {
        if (!principal.taskBound()) {
            return java.util.Set.copyOf(runtimeStore.notificationChannelIds(principal.installationId()));
        }
        var job = jobStore.findByIdUnscoped(principal.jobId())
                .orElseThrow(() -> Status.PERMISSION_DENIED.withDescription("Job context is not available").asRuntimeException());
        if (!principal.installationId().equals(job.pluginInstallationId())) {
            throw Status.PERMISSION_DENIED.withDescription("Job installation mismatch").asRuntimeException();
        }
        java.util.Set<String> installationPool = java.util.Set.copyOf(runtimeStore.notificationChannelIds(principal.installationId()));
        return jobStore.notificationChannelIds(principal.jobId()).stream()
                .filter(installationPool::contains)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private OwnerSnapshot runtimeSourceOwner(PluginRuntimePrincipal principal) {
        if (principal.taskBound()) {
            if (principal.executionId() == null) {
                throw Status.PERMISSION_DENIED.withDescription("Execution context is not available").asRuntimeException();
            }
            com.sky.job.scheduler.JobExecution execution = executionStore.findByIdUnscoped(principal.executionId())
                    .orElseThrow(() -> Status.PERMISSION_DENIED.withDescription("Execution context is not available").asRuntimeException());
            if (execution.jobId() != principal.jobId()
                    || !principal.installationId().equals(execution.pluginInstallationId())
                    || !principal.pluginId().equals(execution.pluginId())
                    || !principal.instanceId().equals(execution.instanceId())) {
                throw Status.PERMISSION_DENIED.withDescription("Execution context mismatch").asRuntimeException();
            }
            return new OwnerSnapshot(execution.ownerUserId(), execution.ownerDepartmentId());
        }
        var installation = runtimeStore.findRuntimeByInstallationId(principal.installationId())
                .orElseThrow(() -> Status.PERMISSION_DENIED.withDescription("Plugin installation is not available").asRuntimeException());
        return new OwnerSnapshot(installation.ownerUserId(), installation.ownerDepartmentId());
    }

    private OwnerSnapshot hostSourceOwner(HostNotificationEvent event) {
        if (event.sourceOwnerUserId() != null || event.sourceOwnerDepartmentId() != null) {
            return new OwnerSnapshot(event.sourceOwnerUserId(), event.sourceOwnerDepartmentId());
        }
        if (event.jobId() != null && !event.jobId().isBlank()) {
            try {
                var job = jobStore.findByIdUnscoped(Long.parseLong(event.jobId())).orElse(null);
                if (job != null) return new OwnerSnapshot(job.ownerUserId(), job.ownerDepartmentId());
            } catch (NumberFormatException ignored) {
                // System events may use non-numeric correlation ids.
            }
        }
        if (event.installationId() != null && !event.installationId().isBlank()) {
            var installation = runtimeStore.findRuntimeByInstallationId(event.installationId()).orElse(null);
            if (installation != null) return new OwnerSnapshot(installation.ownerUserId(), installation.ownerDepartmentId());
        }
        return new OwnerSnapshot(null, null);
    }

    private NotificationSendCommand withSourceOwner(NotificationSendCommand command, OwnerSnapshot owner) {
        return new NotificationSendCommand(
                command.sourceType(), command.requestId(), command.installationId(), command.pluginId(),
                command.instanceId(), command.jobId(), command.executionId(), owner.userId(), owner.departmentId(),
                command.channelIds(), command.title(), command.content(), command.severity(), command.labels());
    }

    private record RuleTarget(String installationId, String jobId) {
    }

    private record OwnerSnapshot(String userId, Long departmentId) {
    }
}
