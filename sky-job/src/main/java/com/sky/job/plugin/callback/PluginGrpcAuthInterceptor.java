package com.sky.job.plugin.callback;

import com.sky.job.plugin.security.PluginRuntimePrincipal;
import com.sky.job.plugin.security.PluginRuntimeTokenRegistry;
import com.sky.job.plugin.security.PluginSecurityContexts;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PluginGrpcAuthInterceptor implements ServerInterceptor {
    public static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> PLUGIN_ID = Metadata.Key.of("x-sky-plugin-id", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> INSTANCE_ID = Metadata.Key.of("x-sky-plugin-instance-id", Metadata.ASCII_STRING_MARSHALLER);

    private static final String BEARER_PREFIX = "Bearer ";

    private final PluginRuntimeTokenRegistry tokenRegistry;

    public PluginGrpcAuthInterceptor(PluginRuntimeTokenRegistry tokenRegistry) {
        this.tokenRegistry = tokenRegistry;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next
    ) {
        String authorization = headers.get(AUTHORIZATION);
        String pluginId = headers.get(PLUGIN_ID);
        String instanceId = headers.get(INSTANCE_ID);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX) || isBlank(pluginId) || isBlank(instanceId)) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing plugin runtime credentials"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }
        String token = authorization.substring(BEARER_PREFIX.length());
        Optional<PluginRuntimePrincipal> principal = tokenRegistry.authenticate(pluginId, instanceId, token);
        if (principal.isEmpty()) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid plugin runtime credentials"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }
        Context context = Context.current().withValue(PluginSecurityContexts.PRINCIPAL, principal.get());
        return Contexts.interceptCall(context, call, headers, next);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
