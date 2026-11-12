package com.sky.job.notification;

import com.sky.job.notification.dto.CreateNotificationChannelRequest;
import com.sky.job.notification.dto.CreateNotificationRuleRequest;
import com.sky.job.notification.dto.NotificationChannelResponse;
import com.sky.job.notification.dto.NotificationRuleResponse;
import com.sky.job.notification.dto.NotificationSendHistoryResponse;
import com.sky.job.notification.dto.SendNotificationTestRequest;
import com.sky.job.notification.dto.UpdateNotificationChannelRequest;
import com.sky.job.notification.dto.UpdateNotificationChannelStateRequest;
import com.sky.job.notification.dto.UpdateNotificationRuleRequest;
import com.sky.job.notification.dto.UpdateNotificationRuleStateRequest;
import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.security.AuthenticatedUser;
import com.sky.job.security.DataScopeService;
import com.sky.job.security.PermissionNames;
import com.sky.job.web.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationChannelStore channelStore;
    private final NotificationRuleStore ruleStore;
    private final NotificationSendHistoryStore historyStore;
    private final NotificationService notificationService;
    private final NotificationSecretMasker masker;
    private final DataScopeService dataScopeService;

    public NotificationController(
            NotificationChannelStore channelStore,
            NotificationRuleStore ruleStore,
            NotificationSendHistoryStore historyStore,
            NotificationService notificationService,
            NotificationSecretMasker masker,
            DataScopeService dataScopeService
    ) {
        this.channelStore = channelStore;
        this.ruleStore = ruleStore;
        this.historyStore = historyStore;
        this.notificationService = notificationService;
        this.masker = masker;
        this.dataScopeService = dataScopeService;
    }

    @GetMapping("/channels")
    public PageResponse<NotificationChannelResponse> listChannels(
            @RequestParam(required = false) NotificationChannelType channelType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String channelId,
            @RequestParam(required = false) String displayName,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        var scope = dataScopeService.resolve(user, PermissionNames.NOTIFICATION_CHANNEL_READ);
        List<NotificationChannelResponse> items = channelStore.list(channelType, enabled, channelId, displayName, scope, page, size).stream()
                .map(channel -> NotificationChannelResponse.from(channel, masker))
                .toList();
        return new PageResponse<>(items, normalizedPage(page), normalizedSize(size), channelStore.count(channelType, enabled, channelId, displayName, scope));
    }

    @GetMapping("/channels/{channelId}")
    public NotificationChannelResponse getChannel(@PathVariable String channelId, @AuthenticationPrincipal AuthenticatedUser user) {
        NotificationChannel channel = channelStore.findByChannelId(channelId, dataScopeService.resolve(user, PermissionNames.NOTIFICATION_CHANNEL_READ)).orElseThrow(() -> new ResourceNotFoundException("Notification channel not found"));
        return NotificationChannelResponse.from(channel, masker);
    }

    @PostMapping("/channels")
    public void createChannel(@RequestBody CreateNotificationChannelRequest request, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        notificationService.createChannel(request, user, httpRequest);
    }

    @PutMapping("/channels/{channelId}")
    public void updateChannel(@PathVariable String channelId, @RequestBody UpdateNotificationChannelRequest request, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        notificationService.updateChannel(channelId, request, user, httpRequest);
    }

    @PatchMapping("/channels/{channelId}/enabled")
    public void updateEnabled(@PathVariable String channelId, @RequestBody UpdateNotificationChannelStateRequest request, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        notificationService.updateEnabled(channelId, request.enabled(), user, httpRequest);
    }

    @DeleteMapping("/channels/{channelId}")
    public void deleteChannel(@PathVariable String channelId, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        notificationService.delete(channelId, user, httpRequest);
    }

    @PostMapping("/channels/{channelId}/test")
    public List<NotificationSendReceipt> testSend(@PathVariable String channelId, @RequestBody SendNotificationTestRequest request, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        return notificationService.testSend(channelId, request.title(), request.content(), request.severity(), user, httpRequest);
    }

    @GetMapping("/rules")
    public PageResponse<NotificationRuleResponse> listRules(
            @RequestParam(required = false) NotificationEventType eventType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String pluginInstallationId,
            @RequestParam(required = false) String jobId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        var scope = dataScopeService.resolve(user, PermissionNames.NOTIFICATION_RULE_READ);
        List<NotificationRuleResponse> items = ruleStore.list(eventType, enabled, pluginInstallationId, jobId, scope, page, size).stream()
                .map(NotificationRuleResponse::from)
                .toList();
        return new PageResponse<>(items, normalizedPage(page), normalizedSize(size), ruleStore.count(eventType, enabled, pluginInstallationId, jobId, scope));
    }

    @GetMapping("/rules/{ruleId}")
    public NotificationRuleResponse getRule(@PathVariable String ruleId, @AuthenticationPrincipal AuthenticatedUser user) {
        return ruleStore.findByRuleId(ruleId, dataScopeService.resolve(user, PermissionNames.NOTIFICATION_RULE_READ))
                .map(NotificationRuleResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Notification rule not found"));
    }

    @PostMapping("/rules")
    public void createRule(@RequestBody CreateNotificationRuleRequest request, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        notificationService.createRule(request, user, httpRequest);
    }

    @PutMapping("/rules/{ruleId}")
    public void updateRule(@PathVariable String ruleId, @RequestBody UpdateNotificationRuleRequest request, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        notificationService.updateRule(ruleId, request, user, httpRequest);
    }

    @PatchMapping("/rules/{ruleId}/enabled")
    public void updateRuleEnabled(@PathVariable String ruleId, @RequestBody UpdateNotificationRuleStateRequest request, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        notificationService.updateRuleEnabled(ruleId, request.enabled(), user, httpRequest);
    }

    @DeleteMapping("/rules/{ruleId}")
    public void deleteRule(@PathVariable String ruleId, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        notificationService.deleteRule(ruleId, user, httpRequest);
    }

    @GetMapping("/history")
    public PageResponse<NotificationSendHistoryResponse> listHistory(
            @RequestParam(required = false) NotificationSourceType sourceType,
            @RequestParam(required = false) String channelId,
            @RequestParam(required = false) NotificationChannelType channelType,
            @RequestParam(required = false) NotificationSendStatus status,
            @RequestParam(required = false) String installationId,
            @RequestParam(required = false) String pluginId,
            @RequestParam(required = false) String jobId,
            @RequestParam(required = false) Long executionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        var scope = dataScopeService.resolve(user, PermissionNames.NOTIFICATION_HISTORY_READ);
        List<NotificationSendHistoryResponse> items = historyStore.list(sourceType, channelId, channelType, status, installationId, pluginId, jobId, executionId, startTime, endTime, scope, page, size).stream()
                .map(NotificationSendHistoryResponse::from)
                .toList();
        long total = historyStore.count(sourceType, channelId, channelType, status, installationId, pluginId, jobId, executionId, startTime, endTime, scope);
        return new PageResponse<>(items, normalizedPage(page), normalizedSize(size), total);
    }

    @GetMapping("/history/{id}")
    public NotificationSendHistoryResponse getHistory(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return historyStore.findById(id, dataScopeService.resolve(user, PermissionNames.NOTIFICATION_HISTORY_READ))
                .map(NotificationSendHistoryResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Notification send history not found"));
    }

    @PostMapping("/history/{id}/retry")
    public void retryHistory(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        notificationService.manualRetry(id, user, httpRequest);
    }

    private int normalizedPage(Integer page) {
        return Math.max(page == null ? 1 : page, 1);
    }

    private int normalizedSize(Integer size) {
        return Math.min(Math.max(size == null ? 20 : size, 1), 100);
    }
}
