package com.sky.job.plugin.artifact;

import com.fasterxml.jackson.databind.JsonNode;

public record PluginUploadManifest(
        String pluginId,
        String version,
        String displayName,
        RuntimeDescriptor runtime,
        CapabilityDescriptor capabilities,
        JsonNode configSchema,
        JsonNode accountSchema
) {
    public record RuntimeDescriptor(String type, String mainClass) {
    }

    public record CapabilityDescriptor(
            boolean task,
            boolean config,
            boolean account,
            boolean notification,
            boolean service
    ) {
    }
}
