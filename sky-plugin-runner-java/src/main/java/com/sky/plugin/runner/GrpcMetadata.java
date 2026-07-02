package com.sky.plugin.runner;

import io.grpc.Metadata;

final class GrpcMetadata {
    static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    static final Metadata.Key<String> PLUGIN_ID = Metadata.Key.of("x-sky-plugin-id", Metadata.ASCII_STRING_MARSHALLER);
    static final Metadata.Key<String> INSTANCE_ID = Metadata.Key.of("x-sky-plugin-instance-id", Metadata.ASCII_STRING_MARSHALLER);

    private GrpcMetadata() {
    }
}
