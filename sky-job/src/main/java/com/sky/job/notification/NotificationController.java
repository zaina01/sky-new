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

    public NotificationController(
            NotificationChannelStore channelStore,
            NotificationRuleStore ruleStore,
            NotificationSendHistoryStore historyStore,
            NotificationService notificationService,
            NotificationSecretMasker masker
    ) {
        this.channelStore = channelStore;
        this.ruleStore = ruleStore;
        this.historyStore = historyStore;
        this.notificationService = notificationService;
        this.masker = masker;
    }

    @GetMapping("/channels")
    public PageResponse<NotificationChannelResponse> listChannels(
            @RequestParam(required = false) NotificationChannelType channelType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String channelId,
            @RequestParam(required = false) String displayName,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        List<NotificationChannelResponse> items = channelStore.list(channelType, enabled, channelId, displayName, page, size).stream()
                .map(channel -> NotificationChannelResponse.from(channel, masker))
                .toList();
        return new PageResponse<>(items, normalizedPage(page), normalizedSize(size), channelStore.count(channelType, enabled, channelId, displayName));
    }

    @GetMapping("/channels/{channelId}")
    public NotificationChannelResponse getChannel(@PathVariable String channelId) {
        NotificationChannel channel = channelStore.findByChannelId(channelId).orElseThrow(() -> new IllegalArgumentException("Notification channel not found"));
        return NotificationChannelResponse.from(channel, masker);
    }

    @PostMapping("/channels")
    public void createChannel(@RequestBody CreateNotificationChannelRequest request, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        notificationService.createChannel(request, user.username(), httpRequest);
    }

    @PutMapping("/channels/{channelId}")
    public void updateChannel(@PathVariable String channelId, @RequestBody UpdateNotificationChannelRequest request, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        notificationService.updateChannel(channelId, request, user.username(), httpRequest);
    }

    @PatchMapping("/channels/{channelId}/enabled")
    public void updateEnabled(@PathVariable String channelId, @RequestBody UpdateNotificationChannelStateRequest request, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        notificationService.updateEnabled(channelId, request.enabled(), user.username(), httpRequest);
    }

    @DeleteMapping("/channels/{channelId}")
    public void deleteChannel(@PathVariable String channelId, HttpServletRequest httpRequest) {
        notificationService.delete(channelId, httpRequest);
    }

    @PostMapping("/channels/{channelId}/test")
    public List<NotificationSendReceipt> testSend(@PathVariable String channelId, @RequestBody SendNotificationTestRequest request, HttpServletRequest httpRequest) {
        return notificationService.testSend(channelId, request.title(), request.content(), request.severity(), httpRequest);
    }

    @GetMapping("/rules")
    public PageResponse<NotificationRuleResponse> listRules(
            @RequestParam(required = false) NotificationEventType eventType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String pluginId,
            @RequestParam(required = false) String jobId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        List<NotificationRuleResponse> items = ruleStore.list(eventType, enabled, pluginId, jobId, page, size).stream()
                .map(NotificationRuleResponse::from)
                .toList();
        return new PageResponse<>(items, normalizedPage(page), normalizedSize(size), ruleStore.count(eventType, enabled, pluginId, jobId));
    }

    @GetMapping("/rules/{ruleId}")
    public NotificationRuleResponse getRule(@PathVariable String ruleId) {
        return ruleStore.findByRuleId(ruleId)
                .map(NotificationRuleResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Notification rule not found"));
    }

    @PostMapping("/rules")
    public void createRule(@RequestBody CreateNotificationRuleRequest request, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        notificationService.createRule(request, user.username(), httpRequest);
    }

    @PutMapping("/rules/{ruleId}")
    public void updateRule(@PathVariable String ruleId, @RequestBody UpdateNotificationRuleRequest request, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        notificationService.updateRule(ruleId, request, user.username(), httpRequest);
    }

    @PatchMapping("/rules/{ruleId}/enabled")
    public void updateRuleEnabled(@PathVariable String ruleId, @RequestBody UpdateNotificationRuleStateRequest request, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        notificationService.updateRuleEnabled(ruleId, request.enabled(), user.username(), httpRequest);
    }

    @DeleteMapping("/rules/{ruleId}")
    public void deleteRule(@PathVariable String ruleId, HttpServletRequest httpRequest) {
        notificationService.deleteRule(ruleId, httpRequest);
    }

    @GetMapping("/history")
    public PageResponse<NotificationSendHistoryResponse> listHistory(
            @RequestParam(required = false) NotificationSourceType sourceType,
            @RequestParam(required = false) String channelId,
            @RequestParam(required = false) NotificationChannelType channelType,
            @RequestParam(required = false) NotificationSendStatus status,
            @RequestParam(required = false) String pluginId,
            @RequestParam(required = false) String jobId,
            @RequestParam(required = false) Long executionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        List<NotificationSendHistoryResponse> items = historyStore.list(sourceType, channelId, channelType, status, pluginId, jobId, executionId, startTime, endTime, page, size).stream()
                .map(NotificationSendHistoryResponse::from)
                .toList();
        long total = historyStore.count(sourceType, channelId, channelType, status, pluginId, jobId, executionId, startTime, endTime);
        return new PageResponse<>(items, normalizedPage(page), normalizedSize(size), total);
    }

    @GetMapping("/history/{id}")
    public NotificationSendHistoryResponse getHistory(@PathVariable long id) {
        return historyStore.findById(id)
                .map(NotificationSendHistoryResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Notification send history not found"));
    }

    @PostMapping("/history/{id}/retry")
    public void retryHistory(@PathVariable long id, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {
        notificationService.manualRetry(id, user.username(), httpRequest);
    }

    private int normalizedPage(Integer page) {
        return Math.max(page == null ? 1 : page, 1);
    }

    private int normalizedSize(Integer size) {
        return Math.min(Math.max(size == null ? 20 : size, 1), 100);
    }
}
