package com.sky.plugin.runner;

import com.sky.plugin.protocol.AccountRequest;
import com.sky.plugin.protocol.AccountResponse;
import com.sky.plugin.protocol.ConfigRequest;
import com.sky.plugin.protocol.ConfigResponse;
import com.sky.plugin.protocol.NotificationChannelListRequest;
import com.sky.plugin.protocol.NotificationChannelListResponse;
import com.sky.plugin.protocol.NotificationRequest;
import com.sky.plugin.protocol.NotificationResponse;
import com.sky.plugin.protocol.PluginDataRequest;
import com.sky.plugin.protocol.PluginDataResponse;
import com.sky.plugin.protocol.PluginHostServiceGrpc;
import com.sky.plugin.protocol.PluginServiceCallRequest;
import com.sky.plugin.protocol.PluginServiceCallResponse;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.netty.shaded.io.netty.channel.EventLoopGroup;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

class HostGrpcClient implements AutoCloseable, NotificationHostGateway {
    private final ManagedChannel channel;
    private final PluginHostServiceGrpc.PluginHostServiceBlockingStub blockingStub;

    private HostGrpcClient(ManagedChannel channel, PluginHostServiceGrpc.PluginHostServiceBlockingStub blockingStub) {
        this.channel = channel;
        this.blockingStub = blockingStub;
    }

    static HostGrpcClient create(RunnerEnvironment environment, EventLoopGroup eventLoopGroup) {
        ManagedChannel channel = NettyChannelBuilder.forAddress(new InetSocketAddress(environment.host(), environment.hostPort()))
                .eventLoopGroup(eventLoopGroup)
                .channelType(io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel.class)
                .usePlaintext()
                .build();
        Metadata metadata = new Metadata();
        metadata.put(GrpcMetadata.AUTHORIZATION, "Bearer " + environment.runtimeToken());
        metadata.put(GrpcMetadata.PLUGIN_ID, environment.pluginId());
        metadata.put(GrpcMetadata.INSTANCE_ID, environment.instanceId());
        return new HostGrpcClient(channel, PluginHostServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata)));
    }

    @Override
    public void close() {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                channel.shutdownNow();
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }

    ConfigResponse getConfig(ConfigRequest request) {
        return blockingStub.getConfig(request);
    }

    ConfigResponse updateConfig(ConfigRequest request) {
        return blockingStub.updateConfig(request);
    }

    AccountResponse listAccounts(AccountRequest request) {
        return blockingStub.listAccounts(request);
    }

    AccountResponse getAccount(AccountRequest request) {
        return blockingStub.getAccount(request);
    }

    AccountResponse updateAccount(AccountRequest request) {
        return blockingStub.updateAccount(request);
    }

    PluginDataResponse getData(PluginDataRequest request) {
        return blockingStub.getData(request);
    }

    PluginDataResponse putData(PluginDataRequest request) {
        return blockingStub.putData(request);
    }

    PluginDataResponse deleteData(PluginDataRequest request) {
        return blockingStub.deleteData(request);
    }

    PluginDataResponse listData(PluginDataRequest request) {
        return blockingStub.listData(request);
    }

    @Override
    public NotificationChannelListResponse listNotificationChannels(NotificationChannelListRequest request) {
        return blockingStub.listNotificationChannels(request);
    }

    @Override
    public NotificationResponse sendNotification(NotificationRequest request) {
        return blockingStub.sendNotification(request);
    }

    PluginServiceCallResponse callPluginService(PluginServiceCallRequest request) {
        return blockingStub.callPluginService(request);
    }
}
