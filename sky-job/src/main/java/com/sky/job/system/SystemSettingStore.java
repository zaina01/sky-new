package com.sky.job.system;

import com.sky.job.system.mapper.SystemSettingMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class SystemSettingStore {
    private final SystemSettingMapper mapper;

    public SystemSettingStore(SystemSettingMapper mapper) {
        this.mapper = mapper;
    }

    public List<SystemSetting> list(String category) {
        return mapper.list(trim(category));
    }

    public List<SystemSetting> listAll() {
        return mapper.listAll();
    }

    public Optional<SystemSetting> findByKey(String settingKey) {
        return mapper.findByKey(settingKey);
    }

    public void insertIfAbsent(SystemSettingDefinition definition, String valueText) {
        mapper.insertIfAbsent(definition.key(), definition.category(), valueText, definition.valueType().name(), definition.description(), true, "SYSTEM");
    }

    public int updateValue(String settingKey, String valueText, String updatedBy) {
        return mapper.updateValue(settingKey, valueText, updatedBy == null ? "" : updatedBy);
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
