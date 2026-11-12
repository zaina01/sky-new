package com.sky.plugin.runner;

import com.sky.plugin.api.NotificationChannelInfo;
import com.sky.plugin.api.NotificationSendRequest;
import com.sky.plugin.api.NotificationSendResult;
import com.sky.plugin.api.PluginNotificationClient;
import com.sky.plugin.protocol.NotificationChannelListRequest;
import com.sky.plugin.protocol.NotificationChannelListResponse;
import com.sky.plugin.protocol.NotificationRequest;
import com.sky.plugin.protocol.NotificationResponse;
import com.sky.plugin.protocol.TaskRunRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Consumer;

class DefaultPluginNotificationClient implements PluginNotificationClient {
    private static final Logger log = LoggerFactory.getLogger(DefaultPluginNotificationClient.class);
    private static final int MAX_LOG_VALUE_CHARS = 500;
    private static final int MAX_RECEIPT_SUMMARY_CHARS = 2_000;

    private final String pluginId;
    private final String jobId;
    private final String requestId;
    private final NotificationHostGateway hostClient;
    private final Consumer<String> rejectionReporter;

    DefaultPluginNotificationClient(TaskRunRequest taskRunRequest, NotificationHostGateway hostClient) {
        this(taskRunRequest.getPluginId(), taskRunRequest.getJobId(), taskRunRequest.getRequestId(), hostClient);
    }

    DefaultPluginNotificationClient(String pluginId, String jobId, String requestId, NotificationHostGateway hostClient) {
        this(pluginId, jobId, requestId, hostClient, message -> log.warn("{}", message));
    }

    DefaultPluginNotificationClient(
            String pluginId,
            String jobId,
            String requestId,
            NotificationHostGateway hostClient,
            Consumer<String> rejectionReporter
    ) {
        this.pluginId = pluginId;
        this.jobId = jobId;
        this.requestId = requestId;
        this.hostClient = hostClient;
        this.rejectionReporter = rejectionReporter;
    }

    @Override
    public List<NotificationChannelInfo> listChannels() {
        NotificationChannelListResponse response = hostClient.listNotificationChannels(NotificationChannelListRequest.newBuilder()
                .setPluginId(pluginId)
                .build());
        return response.getChannelsList().stream()
                .map(channel -> new NotificationChannelInfo(channel.getChannelId(), channel.getChannelType(), channel.getDisplayName(), channel.getEnabled()))
                .toList();
    }

    @Override
    public NotificationSendResult send(String channelId, String title, String content) {
        return send(new NotificationSendRequest(List.of(channelId), title, content, "INFO", null));
    }

    @Override
    public NotificationSendResult sendAll(String title, String content) {
        return sendAll(new NotificationSendRequest(List.of(), title, content, "INFO", null));
    }

    @Override
    public NotificationSendResult sendAll(NotificationSendRequest request) {
        List<String> channelIds = listChannels().stream()
                .map(NotificationChannelInfo::channelId)
                .toList();
        return send(new NotificationSendRequest(channelIds, request.title(), request.content(), request.severity(), request.labels()));
    }

    @Override
    public NotificationSendResult send(NotificationSendRequest request) {
        NotificationRequest grpcRequest = NotificationRequest.newBuilder()
                .setPluginId(pluginId)
                .setJobId(jobId)
                .setRequestId(requestId)
                .addAllChannelIds(request.channelIds())
                .setTitle(value(request.title()))
                .setContent(value(request.content()))
                .setSeverity(value(request.severity()))
                .putAllLabels(request.labels())
                .build();
        NotificationResponse response = hostClient.sendNotification(grpcRequest);
        if (!response.getSuccess()) {
            rejectionReporter.accept(rejectionLogMessage(grpcRequest, response));
        }
        return new NotificationSendResult(
                response.getSuccess(),
                response.getMessage(),
                response.getReceiptsList().stream()
                        .map(receipt -> new NotificationSendResult.Receipt(receipt.getChannelId(), receipt.getStatus(), receipt.getMessage(), receipt.getHistoryId()))
                        .toList()
        );
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String rejectionLogMessage(NotificationRequest request, NotificationResponse response) {
        return "Plugin notification rejected: pluginId=" + safeLogValue(request.getPluginId(), MAX_LOG_VALUE_CHARS)
                + ", jobId=" + safeLogValue(request.getJobId(), MAX_LOG_VALUE_CHARS)
                + ", requestId=" + safeLogValue(request.getRequestId(), MAX_LOG_VALUE_CHARS)
                + ", channelIds=" + channelIds(request.getChannelIdsList())
                + ", titleBytes=" + utf8Bytes(request.getTitle())
                + ", contentBytes=" + utf8Bytes(request.getContent())
                + ", reason=" + safeLogValue(response.getMessage(), MAX_LOG_VALUE_CHARS)
                + ", receipts=" + receiptSummary(response);
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
}
