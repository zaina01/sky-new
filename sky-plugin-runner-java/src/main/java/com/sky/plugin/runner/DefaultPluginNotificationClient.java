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

import java.util.List;

class DefaultPluginNotificationClient implements PluginNotificationClient {
    private final String pluginId;
    private final String jobId;
    private final String requestId;
    private final HostGrpcClient hostClient;

    DefaultPluginNotificationClient(TaskRunRequest taskRunRequest, HostGrpcClient hostClient) {
        this(taskRunRequest.getPluginId(), taskRunRequest.getJobId(), taskRunRequest.getRequestId(), hostClient);
    }

    DefaultPluginNotificationClient(String pluginId, String jobId, String requestId, HostGrpcClient hostClient) {
        this.pluginId = pluginId;
        this.jobId = jobId;
        this.requestId = requestId;
        this.hostClient = hostClient;
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
}
