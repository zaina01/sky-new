package com.sky.job.system;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(50)
public class SystemSettingBootstrap implements ApplicationRunner {
    private final SystemSettingDefinitions definitions;
    private final SystemSettingCodec codec;
    private final SystemSettingStore store;
    private final SystemSettingProvider provider;

    public SystemSettingBootstrap(SystemSettingDefinitions definitions, SystemSettingCodec codec, SystemSettingStore store, SystemSettingProvider provider) {
        this.definitions = definitions;
        this.codec = codec;
        this.store = store;
        this.provider = provider;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (SystemSettingDefinition definition : definitions.all()) {
            store.insertIfAbsent(definition, definition.defaultValueText(codec));
        }
        provider.reload();
    }
}
