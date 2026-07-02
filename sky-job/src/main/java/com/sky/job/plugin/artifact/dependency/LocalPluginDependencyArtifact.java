package com.sky.job.plugin.artifact.dependency;

import java.time.LocalDateTime;

public record LocalPluginDependencyArtifact(
        long id,
        String groupId,
        String artifactId,
        String version,
        String classifier,
        String extension,
        String coordinate,
        String relativePath,
        String sha256,
        String originalFilename,
        long sizeBytes,
        String uploadedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
