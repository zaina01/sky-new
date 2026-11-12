package com.sky.job.plugin.storage;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PluginAuditLogMapperXmlTest {
    @Test
    void capturesOwnershipAtInsertAndFiltersHistoricalRowsBySnapshot() throws Exception {
        URL resource = getClass().getClassLoader().getResource("mapper/plugin/storage/PluginAuditLogMapper.xml");

        assertThat(resource).isNotNull();
        String mapperXml = new String(resource.openStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(mapperXml)
                .contains("owner_user_id, owner_dept_id")
                .contains("SELECT owner_user_id FROM plugin_installation")
                .contains("SELECT owner_dept_id FROM plugin_installation")
                .contains("l.owner_dept_id IN")
                .contains("l.owner_user_id = #{scope.userId}")
                .doesNotContain("JOIN plugin_installation i ON i.installation_id = l.installation_id");
    }
}
