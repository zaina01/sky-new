package com.sky.job.security;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HistoricalDataScopeMapperXmlTest {
    @Test
    void notificationHistoryIsVisibleFromEitherSourceOrChannelOwnershipSnapshot() throws Exception {
        String xml = mapper("mapper/notification/NotificationSendHistoryMapper.xml");
        String scope = statement(xml, "scope", "sql");

        assertThat(scope)
                .contains("h.source_owner_dept_id IN")
                .contains("OR h.channel_owner_dept_id IN")
                .contains("h.source_owner_user_id=#{scope.userId} OR h.channel_owner_user_id=#{scope.userId}")
                .contains("h.source_owner_user_id IS NOT NULL")
                .contains("scope.denied");
    }

    @Test
    void serviceCallHistoryIsVisibleFromEitherEndpointSnapshot() throws Exception {
        String xml = mapper("mapper/plugin/callback/PluginServiceCallLogMapper.xml");
        String scope = statement(xml, "scope", "sql");

        assertThat(scope)
                .contains("caller_owner_dept_id IN")
                .contains("OR target_owner_dept_id IN")
                .contains("caller_owner_user_id = #{scope.userId} OR target_owner_user_id = #{scope.userId}")
                .contains("scope.denied");
    }

    private String mapper(String resourceName) throws Exception {
        URL resource = getClass().getClassLoader().getResource(resourceName);
        assertThat(resource).isNotNull();
        return new String(resource.openStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private String statement(String xml, String id, String element) {
        int start = xml.indexOf("<" + element + " id=\"" + id + "\"");
        assertThat(start).isGreaterThanOrEqualTo(0);
        int end = xml.indexOf("</" + element + ">", start);
        assertThat(end).isGreaterThan(start);
        return xml.substring(start, end);
    }
}
