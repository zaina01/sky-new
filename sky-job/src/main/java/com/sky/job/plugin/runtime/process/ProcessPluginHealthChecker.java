package com.sky.job.plugin.runtime.process;

import com.sky.job.plugin.callback.PluginGrpcAuthInterceptor;
import com.sky.job.plugin.runtime.PluginRuntimeHandle;
import com.sky.plugin.protocol.HealthRequest;
import com.sky.plugin.protocol.PluginRunnerServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.channel.EventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class ProcessPluginHealthChecker {
    private final EventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
    private final ProcessPluginRuntimeProperties properties;

    public ProcessPluginHealthChecker(ProcessPluginRuntimeProperties properties) {
        this.properties = properties;
    }

    @PreDestroy
    public void destroy() {
        eventLoopGroup.shutdownGracefully();
    }

    public void awaitHealthy(PluginRuntimeHandle handle, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        StatusRuntimeException lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            if (handle instanceof ProcessPluginRuntimeHandle process && !process.process().isAlive()) {
                throw new IllegalStateException("Plugin runner exited before becoming healthy; see " + handle.logPath());
            }
            ManagedChannel channel = NettyChannelBuilder.forAddress(new InetSocketAddress("127.0.0.1", handle.port()))
                    .eventLoopGroup(eventLoopGroup)
                    .channelType(NioSocketChannel.class)
                    .usePlaintext()
                    .maxInboundMessageSize(properties.maxInboundMessageBytes())
                    .build();
            try {
                Metadata metadata = new Metadata();
                metadata.put(PluginGrpcAuthInterceptor.AUTHORIZATION, "Bearer " + handle.token());
                metadata.put(PluginGrpcAuthInterceptor.PLUGIN_ID, handle.pluginId());
                metadata.put(PluginGrpcAuthInterceptor.INSTANCE_ID, handle.instanceId());
                PluginRunnerServiceGrpc.PluginRunnerServiceBlockingStub stub = PluginRunnerServiceGrpc.newBlockingStub(channel)
                        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                        .withDeadlineAfter(500, TimeUnit.MILLISECONDS);
                if (stub.health(HealthRequest.newBuilder()
                        .setPluginId(handle.pluginId())
                        .setInstanceId(handle.instanceId())
                        .build()).getHealthy()) {
                    return;
                }
            } catch (StatusRuntimeException exception) {
                lastFailure = exception;
            } finally {
                close(channel);
            }
        }
        throw new IllegalStateException("Plugin runner did not become healthy", lastFailure);
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
}
