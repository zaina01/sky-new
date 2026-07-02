package com.sky.job.plugin.callback;

import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;

@Component
public class PluginHostGrpcServer implements SmartInitializingSingleton {
    private final String bindHost;
    private final int port;
    private final int maxInboundMessageBytes;
    private final PluginCallbackService callbackService;
    private final PluginGrpcAuthInterceptor authInterceptor;
    private Server server;

    public PluginHostGrpcServer(
            @Value("${sky.plugin.host-grpc.bind-host:127.0.0.1}") String bindHost,
            @Value("${sky.plugin.host-grpc.port}") int port,
            @Value("${sky.plugin.host-grpc.max-inbound-message-bytes:1048576}") int maxInboundMessageBytes,
            PluginCallbackService callbackService,
            PluginGrpcAuthInterceptor authInterceptor
    ) {
        this.bindHost = bindHost;
        this.port = port;
        this.maxInboundMessageBytes = maxInboundMessageBytes;
        this.callbackService = callbackService;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            server = NettyServerBuilder.forAddress(new InetSocketAddress(bindHost, port))
                    .maxInboundMessageSize(maxInboundMessageBytes)
                    .addService(ServerInterceptors.intercept(new PluginHostGrpcService(callbackService), authInterceptor))
                    .build()
                    .start();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start plugin host gRPC server", exception);
        }
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.shutdownNow();
        }
    }
}
