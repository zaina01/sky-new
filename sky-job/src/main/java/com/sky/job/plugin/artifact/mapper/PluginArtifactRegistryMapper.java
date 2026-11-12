package com.sky.job.plugin.artifact.mapper;

import com.sky.job.plugin.artifact.PluginDefinition;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PluginArtifactRegistryMapper {
    int upsertDefinition(
            @Param("pluginId") String pluginId,
            @Param("version") String version,
            @Param("displayName") String displayName,
            @Param("mainClass") String mainClass,
            @Param("artifactPath") String artifactPath,
            @Param("artifactSha256") String artifactSha256,
            @Param("capabilitiesJson") String capabilitiesJson,
            @Param("configSchemaJson") String configSchemaJson,
            @Param("accountSchemaJson") String accountSchemaJson,
            @Param("dependencyJarsJson") String dependencyJarsJson,
            @Param("overwriteDependencies") boolean overwriteDependencies,
            @Param("uploadedBy") String uploadedBy
    );

    int updateDependencyJars(
            @Param("pluginId") String pluginId,
            @Param("dependencyJarsJson") String dependencyJarsJson
    );

    PluginDefinition findDefinition(@Param("pluginId") String pluginId);

    List<PluginDefinition> listDefinitions(@Param("keyword") String keyword, @Param("keywordLike") String keywordLike, @Param("limit") int limit, @Param("offset") int offset);

    long countDefinitions(@Param("keyword") String keyword, @Param("keywordLike") String keywordLike);

    int countInstallations(@Param("pluginId") String pluginId);

    int deleteDefinition(@Param("pluginId") String pluginId);
}
