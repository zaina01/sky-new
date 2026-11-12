package com.sky.plugin.api;

public interface PluginServiceClient {
    PluginServiceResponse call(String targetPluginId, String operation, String requestJson);

    /**
     * Calls a concrete plugin installation. New integrations should prefer
     * this method so that a definition with multiple installations is never
     * resolved ambiguously.
     */
    default PluginServiceResponse callInstallation(String targetInstallationId, String operation, String requestJson) {
        throw new UnsupportedOperationException("Explicit plugin installation calls are not supported by this client");
    }
}
