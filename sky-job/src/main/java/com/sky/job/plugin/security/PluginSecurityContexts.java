package com.sky.job.plugin.security;

import io.grpc.Context;

public final class PluginSecurityContexts {
    public static final Context.Key<PluginRuntimePrincipal> PRINCIPAL = Context.key("plugin-runtime-principal");

    private PluginSecurityContexts() {
    }

    public static PluginRuntimePrincipal currentPrincipal() {
        PluginRuntimePrincipal principal = PRINCIPAL.get();
        if (principal == null) {
            throw new IllegalStateException("No plugin runtime principal in gRPC context");
        }
        return principal;
    }
}
