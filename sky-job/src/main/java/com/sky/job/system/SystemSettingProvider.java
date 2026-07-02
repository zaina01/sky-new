package com.sky.job.system;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SystemSettingProvider implements SystemSettingDefinition.SystemSettingLookup {
    private final SystemSettingStore store;
    private final SystemSettingDefinitions definitions;
    private final SystemSettingCodec codec;
    private final Map<String, String> values = new ConcurrentHashMap<>();

    public SystemSettingProvider(SystemSettingStore store, SystemSettingDefinitions definitions, SystemSettingCodec codec) {
        this.store = store;
        this.definitions = definitions;
        this.codec = codec;
    }

    public void reload() {
        values.clear();
        for (SystemSetting setting : store.listAll()) {
            if (definitions.contains(setting.settingKey())) {
                values.put(setting.settingKey(), setting.valueText());
            }
        }
    }

    public void put(String key, String valueText) {
        values.put(key, valueText);
    }

    public Object typedValue(SystemSettingDefinition definition, String valueText) {
        return codec.typedValue(definition.valueType(), valueText);
    }

    @Override
    public boolean getBoolean(String key) {
        return (Boolean) parsed(key);
    }

    @Override
    public int getInt(String key) {
        return (Integer) parsed(key);
    }

    @Override
    public long getLong(String key) {
        return (Long) parsed(key);
    }

    @Override
    public Duration getDuration(String key) {
        return (Duration) parsed(key);
    }

    @Override
    public String getString(String key) {
        return (String) parsed(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getStringList(String key) {
        return (List<String>) parsed(key);
    }

    private Object parsed(String key) {
        SystemSettingDefinition definition = definitions.require(key);
        String valueText = values.get(key);
        try {
            return codec.parse(definition.valueType(), valueText == null ? definition.defaultValue().get() : valueText);
        } catch (IllegalArgumentException exception) {
            return codec.parse(definition.valueType(), definition.defaultValue().get());
        }
    }
}
