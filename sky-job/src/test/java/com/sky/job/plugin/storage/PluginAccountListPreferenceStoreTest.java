package com.sky.job.plugin.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.plugin.storage.mapper.PluginAccountListPreferenceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginAccountListPreferenceStoreTest {
    private PluginAccountListPreferenceMapper mapper;
    private PluginAccountListPreferenceStore store;

    @BeforeEach
    void setUp() {
        mapper = mock(PluginAccountListPreferenceMapper.class);
        store = new PluginAccountListPreferenceStore(mapper, new ObjectMapper());
    }

    @Test
    void preservesSelectedFieldOrder() {
        when(mapper.findSelectedFieldsJson("user-a", "installation-a"))
                .thenReturn("[\"region\",\"username\"]");

        assertThat(store.findFields("user-a", "installation-a"))
                .hasValue(List.of("region", "username"));

        store.upsertFields("user-a", "installation-a", List.of("region", "username"));
        verify(mapper).upsert("user-a", "installation-a", "[\"region\",\"username\"]");
    }
}
