package com.sky.plugin.runner;

import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

class RunnerAuthInterceptor implements ServerInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";

    private final RunnerEnvironment environment;

    RunnerAuthInterceptor(RunnerEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next
    ) {
        String authorization = headers.get(GrpcMetadata.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX) || !matches(authorization.substring(BEARER_PREFIX.length()), environment.runtimeToken())) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid plugin runtime token"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }
        String pluginId = headers.get(GrpcMetadata.PLUGIN_ID);
        String instanceId = headers.get(GrpcMetadata.INSTANCE_ID);
        if (!environment.pluginId().equals(pluginId) || !environment.instanceId().equals(instanceId)) {
            call.close(Status.PERMISSION_DENIED.withDescription("Plugin identity mismatch"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }
        ServerCall<ReqT, RespT> forwardingCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
        };
        return next.startCall(forwardingCall, headers);
    }

    private boolean matches(String provided, String expected) {
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
