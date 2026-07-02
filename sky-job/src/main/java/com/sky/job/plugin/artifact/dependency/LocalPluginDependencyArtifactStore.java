package com.sky.job.plugin.artifact.dependency;

import com.sky.job.plugin.artifact.mapper.LocalPluginDependencyArtifactMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class LocalPluginDependencyArtifactStore {
    private final LocalPluginDependencyArtifactMapper mapper;

    public LocalPluginDependencyArtifactStore(LocalPluginDependencyArtifactMapper mapper) {
        this.mapper = mapper;
    }

    public LocalPluginDependencyArtifact upsert(PluginDependencyDeclaration declaration, String relativePath, String sha256, String originalFilename, long sizeBytes, String uploadedBy) {
        String classifier = "";
        String extension = "jar";
        mapper.upsert(
                declaration.groupId(),
                declaration.artifactId(),
                declaration.version(),
                classifier,
                extension,
                declaration.coordinate(),
                relativePath,
                sha256,
                safe(originalFilename),
                sizeBytes,
                safe(uploadedBy)
        );
        return mapper.findByCoordinate(declaration.groupId(), declaration.artifactId(), declaration.version(), classifier, extension)
                .orElseThrow(() -> new IllegalStateException("Local plugin dependency artifact was not stored"));
    }

    public List<LocalPluginDependencyArtifact> list(String keyword, int limit, int offset) {
        return mapper.list(safe(keyword), limit, offset);
    }

    public long count(String keyword) {
        return mapper.count(safe(keyword));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
