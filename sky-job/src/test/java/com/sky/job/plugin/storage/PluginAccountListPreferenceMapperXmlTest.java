package com.sky.job.plugin.storage;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PluginAccountListPreferenceMapperXmlTest {
    @Test
    void mapperScopesPreferencesByUserAndInstallationAndPreservesJsonOrder() throws Exception {
        URL resource = getClass().getClassLoader()
                .getResource("mapper/plugin/storage/PluginAccountListPreferenceMapper.xml");

        assertThat(resource).isNotNull();
        String mapperXml = new String(resource.openStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(mapperXml)
                .contains("WHERE user_id = #{userId} AND installation_id = #{installationId}")
                .contains("CAST(#{selectedFieldsJson} AS JSON)")
                .contains("ON DUPLICATE KEY UPDATE")
                .contains("WHERE installation_id = #{installationId}");
    }
}
