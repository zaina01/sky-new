package com.sky.job.plugin.artifact.mapper;

import com.sky.job.plugin.artifact.dependency.LocalPluginDependencyArtifact;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

public interface LocalPluginDependencyArtifactMapper {
    void upsert(
            @Param("groupId") String groupId,
            @Param("artifactId") String artifactId,
            @Param("version") String version,
            @Param("classifier") String classifier,
            @Param("extension") String extension,
            @Param("coordinate") String coordinate,
            @Param("relativePath") String relativePath,
            @Param("sha256") String sha256,
            @Param("originalFilename") String originalFilename,
            @Param("sizeBytes") long sizeBytes,
            @Param("uploadedBy") String uploadedBy
    );

    Optional<LocalPluginDependencyArtifact> findByCoordinate(
            @Param("groupId") String groupId,
            @Param("artifactId") String artifactId,
            @Param("version") String version,
            @Param("classifier") String classifier,
            @Param("extension") String extension
    );

    List<LocalPluginDependencyArtifact> list(
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long count(@Param("keyword") String keyword);
}
