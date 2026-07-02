package com.sky.job.plugin.artifact.mapper;

import org.apache.ibatis.annotations.Param;

public interface PluginArtifactRegistryMapper {
    int upsertRuntime(
            @Param("pluginId") String pluginId,
            @Param("instanceId") String instanceId,
            @Param("version") String version,
            @Param("displayName") String displayName,
            @Param("mainClass") String mainClass,
            @Param("artifactPath") String artifactPath,
            @Param("artifactSha256") String artifactSha256,
            @Param("capabilitiesJson") String capabilitiesJson,
            @Param("configSchemaJson") String configSchemaJson,
            @Param("accountSchemaJson") String accountSchemaJson,
            @Param("dependencyJarsJson") String dependencyJarsJson,
            @Param("overwriteDependencies") boolean overwriteDependencies
    );

    int updateDependencyJars(
            @Param("pluginId") String pluginId,
            @Param("dependencyJarsJson") String dependencyJarsJson
    );
}
