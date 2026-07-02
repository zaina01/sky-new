package com.sky.job.plugin.artifact;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "sky.plugin.upload")
public record PluginArtifactProperties(
        Path artifactRoot,
        long maxArtifactBytes,
        String uploadToken
) {
    public PluginArtifactProperties {
        if (artifactRoot == null) artifactRoot = Path.of("./data/plugins/artifacts");
        if (maxArtifactBytes <= 0) maxArtifactBytes = 50L * 1024 * 1024;
        if (uploadToken == null) uploadToken = "";
    }
}
