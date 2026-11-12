package com.sky.job.plugin.artifact;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PluginArtifactRegistryMapperXmlTest {
    @Test
    void upsertUpdatesOnlyGlobalDefinitionMetadata() throws Exception {
        URL resource = getClass().getClassLoader().getResource("mapper/plugin/artifact/PluginArtifactRegistryMapper.xml");

        assertThat(resource).isNotNull();
        String mapperXml = new String(resource.openStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(mapperXml).contains("INSERT INTO plugin_definition");
        assertThat(mapperXml).contains("dependency_jars_json = CASE WHEN");
        assertThat(mapperXml).doesNotContain("plugin_runtime");
    }
}
