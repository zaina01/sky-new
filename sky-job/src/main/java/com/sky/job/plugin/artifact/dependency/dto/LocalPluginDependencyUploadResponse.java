package com.sky.job.plugin.artifact.dependency.dto;

import com.sky.job.plugin.artifact.dependency.LocalPluginDependencyArtifact;

import java.time.LocalDateTime;

public record LocalPluginDependencyUploadResponse(
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
    public static LocalPluginDependencyUploadResponse from(LocalPluginDependencyArtifact artifact) {
        return new LocalPluginDependencyUploadResponse(
                artifact.id(),
                artifact.groupId(),
                artifact.artifactId(),
                artifact.version(),
                artifact.classifier(),
                artifact.extension(),
                artifact.coordinate(),
                artifact.relativePath(),
                artifact.sha256(),
                artifact.originalFilename(),
                artifact.sizeBytes(),
                artifact.uploadedBy(),
                artifact.createdAt(),
                artifact.updatedAt()
        );
    }
}
