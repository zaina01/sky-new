package com.sky.job.system;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

public record SystemSettingDefinition(
        String key,
        String category,
        SystemSettingValueType valueType,
        String displayName,
        String description,
        String unit,
        String group,
        String placeholder,
        Object min,
        Object max,
        Supplier<Object> defaultValue,
        SettingValidator validator
) {
    public String defaultValueText(SystemSettingCodec codec) {
        return codec.format(valueType, defaultValue.get());
    }

    public String normalize(Object value, SystemSettingCodec codec, SystemSettingLookup lookup) {
        Object parsed = codec.parse(valueType, value);
        if (validator != null) {
            validator.validate(parsed, lookup);
        }
        return codec.format(valueType, parsed);
    }

    @FunctionalInterface
    public interface SettingValidator {
        void validate(Object value, SystemSettingLookup lookup);
    }

    public interface SystemSettingLookup {
        boolean getBoolean(String key);

        int getInt(String key);

        long getLong(String key);

        Duration getDuration(String key);

        String getString(String key);

        List<String> getStringList(String key);
    }
}
