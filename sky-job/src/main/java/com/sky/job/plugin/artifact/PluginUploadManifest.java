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
    public record RuntimeDescriptor(String type, String mainClass, String entryPoint, String executionMode) {
        public RuntimeDescriptor(String type, String mainClass, String entryPoint) { this(type, mainClass, entryPoint, "plugin"); }
        public RuntimeDescriptor(String type, String mainClass) { this(type, mainClass, mainClass); }
        public String engine() { return "java-process".equals(type) ? "java" : type; }
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
