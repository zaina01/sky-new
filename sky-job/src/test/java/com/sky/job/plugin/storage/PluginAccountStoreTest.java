package com.sky.job.plugin.storage;

import com.sky.job.plugin.storage.mapper.PluginAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PluginAccountStoreTest {
    private PluginAccountMapper mapper;
    private PluginAccountStore store;

    @BeforeEach
    void setUp() {
        mapper = mock(PluginAccountMapper.class);
        store = new PluginAccountStore(mapper);
    }

    @Test
    void usesContainsPatternForAccountIdAcrossManagedQueries() {
        store.list("demo", true, "account", "name", 1, 20);
        store.count("demo", true, "account", "name");
        store.listManagedForExport("demo", true, "account", "name", 100);

        verify(mapper).listManaged("demo", true, "account", "%account%", "name", "%name%", null, false, 20, 0);
        verify(mapper).countManaged("demo", true, "account", "%account%", "name", "%name%", null, false);
        verify(mapper).listManagedForExport("demo", true, "account", "%account%", "name", "%name%", null, false, 100);
    }
}
