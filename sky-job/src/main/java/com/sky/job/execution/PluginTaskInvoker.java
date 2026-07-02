package com.sky.job.execution;

import com.sky.job.plugin.callback.PluginGrpcAuthInterceptor;
import com.sky.job.plugin.runtime.PluginRuntimeHandle;
import com.sky.plugin.protocol.CancelTaskRequest;
import com.sky.plugin.protocol.CancelTaskResponse;
import com.sky.plugin.protocol.PluginRunnerServiceGrpc;
import com.sky.plugin.protocol.PluginServiceRunRequest;
import com.sky.plugin.protocol.PluginServiceRunResponse;
import com.sky.plugin.protocol.TaskRunRequest;
import com.sky.plugin.protocol.TaskRunResponse;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.channel.EventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class PluginTaskInvoker {
    private final EventLoopGroup eventLoopGroup = new NioEventLoopGroup(2);
    private final int maxInboundMessageBytes;

    public PluginTaskInvoker(@Value("${sky.plugin.runtime.process.max-inbound-message-bytes:1048576}") int maxInboundMessageBytes) {
        this.maxInboundMessageBytes = maxInboundMessageBytes;
    }

    @PreDestroy
    public void destroy() {
        eventLoopGroup.shutdownGracefully();
    }

    public TaskRunResponse invoke(PluginRuntimeHandle handle, TaskRunRequest request, Duration timeout) {
        ManagedChannel channel = channel(handle);
        try {
            return stub(channel, handle, timeout).runTask(request);
        } finally {
            close(channel);
        }
    }

    public PluginServiceRunResponse callService(PluginRuntimeHandle handle, PluginServiceRunRequest request, Duration timeout) {
        ManagedChannel channel = channel(handle);
        try {
            return stub(channel, handle, timeout).callService(request);
        } finally {
            close(channel);
        }
    }

    public CancelTaskResponse cancel(PluginRuntimeHandle handle, String requestId, String jobId, Duration timeout) {
        ManagedChannel channel = channel(handle);
        try {
            CancelTaskRequest request = CancelTaskRequest.newBuilder()
                    .setRequestId(requestId)
                    .setPluginId(handle.pluginId())
                    .setJobId(jobId)
                    .build();
            return stub(channel, handle, timeout).cancelTask(request);
        } finally {
            close(channel);
        }
    }

    private ManagedChannel channel(PluginRuntimeHandle handle) {
        return NettyChannelBuilder.forAddress(new InetSocketAddress("127.0.0.1", handle.port()))
                .eventLoopGroup(eventLoopGroup)
                .channelType(NioSocketChannel.class)
                .usePlaintext()
                .maxInboundMessageSize(maxInboundMessageBytes)
                .build();
    }

    private void close(ManagedChannel channel) {
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

    private PluginRunnerServiceGrpc.PluginRunnerServiceBlockingStub stub(ManagedChannel channel, PluginRuntimeHandle handle, Duration timeout) {
        Metadata metadata = new Metadata();
        metadata.put(PluginGrpcAuthInterceptor.AUTHORIZATION, "Bearer " + handle.token());
        metadata.put(PluginGrpcAuthInterceptor.PLUGIN_ID, handle.pluginId());
        metadata.put(PluginGrpcAuthInterceptor.INSTANCE_ID, handle.instanceId());
        return PluginRunnerServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                .withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
}
