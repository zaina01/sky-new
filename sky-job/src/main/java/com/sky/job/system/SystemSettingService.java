package com.sky.job.system;

import com.sky.job.audit.SystemLogStore;
import com.sky.job.notification.NotificationRetryScheduler;
import com.sky.job.retention.RetentionCleanupScheduler;
import com.sky.job.system.dto.BatchUpdateSystemSettingsRequest;
import com.sky.job.system.dto.SystemSettingResponse;
import com.sky.job.system.dto.UpdateSystemSettingRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SystemSettingService {
    private final SystemSettingStore store;
    private final SystemSettingDefinitions definitions;
    private final SystemSettingCodec codec;
    private final SystemSettingProvider provider;
    private final SystemLogStore systemLogStore;
    private final RetentionCleanupScheduler retentionCleanupScheduler;
    private final NotificationRetryScheduler notificationRetryScheduler;

    public SystemSettingService(
            SystemSettingStore store,
            SystemSettingDefinitions definitions,
            SystemSettingCodec codec,
            SystemSettingProvider provider,
            SystemLogStore systemLogStore,
            RetentionCleanupScheduler retentionCleanupScheduler,
            NotificationRetryScheduler notificationRetryScheduler
    ) {
        this.store = store;
        this.definitions = definitions;
        this.codec = codec;
        this.provider = provider;
        this.systemLogStore = systemLogStore;
        this.retentionCleanupScheduler = retentionCleanupScheduler;
        this.notificationRetryScheduler = notificationRetryScheduler;
    }

    public List<SystemSettingResponse> list(String category) {
        return store.list(category).stream()
                .filter(setting -> definitions.contains(setting.settingKey()))
                .map(this::response)
                .toList();
    }

    public SystemSettingResponse get(String key) {
        definitions.require(key);
        return response(requireSetting(key));
    }

    @Transactional
    public SystemSettingResponse update(String key, UpdateSystemSettingRequest request, String actor, HttpServletRequest httpRequest) {
        String normalized = normalize(key, request == null ? null : request.value(), provider);
        Map<String, String> current = currentValues();
        current.put(key, normalized);
        validateAll(current);
        apply(Map.of(key, normalized), actor, httpRequest);
        return get(key);
    }

    @Transactional
    public List<SystemSettingResponse> updateBatch(BatchUpdateSystemSettingsRequest request, String actor, HttpServletRequest httpRequest) {
        if (request == null || request.values() == null || request.values().isEmpty()) {
            throw new IllegalArgumentException("System setting values are required");
        }
        Map<String, String> current = currentValues();
        Map<String, String> updates = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : request.values().entrySet()) {
            String key = entry.getKey();
            String normalized = normalize(key, entry.getValue(), new MapSettingLookup(current));
            updates.put(key, normalized);
            current.put(key, normalized);
        }
        validateAll(current);
        apply(updates, actor, httpRequest);
        return list(null);
    }

    private void apply(Map<String, String> updates, String actor, HttpServletRequest httpRequest) {
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            requireSetting(entry.getKey());
            if (store.updateValue(entry.getKey(), entry.getValue(), actor) == 0) {
                throw new IllegalArgumentException("System setting not found");
            }
            provider.put(entry.getKey(), entry.getValue());
            systemLogStore.record("SYSTEM_SETTING", "UPDATE_SETTING", "INFO", "SUCCESS", "SETTING", entry.getKey(), "System setting updated", httpRequest);
        }
        refreshSchedulers(updates.keySet());
    }

    private String normalize(String key, Object value, SystemSettingDefinition.SystemSettingLookup lookup) {
        SystemSetting setting = requireSetting(key);
        if (!setting.editable()) {
            throw new IllegalArgumentException("System setting is not editable");
        }
        return definitions.require(key).normalize(value, codec, lookup);
    }

    private SystemSettingResponse response(SystemSetting setting) {
        SystemSettingDefinition definition = definitions.require(setting.settingKey());
        Object value = provider.typedValue(definition, setting.valueText());
        return new SystemSettingResponse(
                setting.settingKey(),
                definition.category(),
                definition.valueType().name(),
                value,
                setting.valueText(),
                definition.displayName(),
                definition.description(),
                definition.unit(),
                definition.group(),
                definition.placeholder(),
                definition.min(),
                definition.max(),
                setting.editable(),
                setting.updatedAt()
        );
    }

    private SystemSetting requireSetting(String key) {
        return store.findByKey(key).orElseThrow(() -> new IllegalArgumentException("System setting not found"));
    }

    private Map<String, String> currentValues() {
        Map<String, String> values = new LinkedHashMap<>();
        for (SystemSetting setting : store.listAll()) {
            if (definitions.contains(setting.settingKey())) {
                values.put(setting.settingKey(), setting.valueText());
            }
        }
        return values;
    }

    private void validateAll(Map<String, String> values) {
        SystemSettingDefinition.SystemSettingLookup lookup = new MapSettingLookup(values);
        for (SystemSettingDefinition definition : definitions.all()) {
            definition.normalize(values.get(definition.key()), codec, lookup);
        }
    }

    private void refreshSchedulers(Set<String> keys) {
        if (keys.stream().anyMatch(SystemSettingDefinitions.RETENTION_SCHEDULER_KEYS::contains)) {
            retentionCleanupScheduler.refresh();
        }
        if (keys.stream().anyMatch(SystemSettingDefinitions.NOTIFICATION_SCHEDULER_KEYS::contains)) {
            notificationRetryScheduler.refresh();
        }
    }

    private class MapSettingLookup implements SystemSettingDefinition.SystemSettingLookup {
        private final Map<String, String> values;

        private MapSettingLookup(Map<String, String> values) {
            this.values = values;
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
        public java.time.Duration getDuration(String key) {
            return (java.time.Duration) parsed(key);
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
            return codec.parse(definition.valueType(), valueText == null ? definition.defaultValue().get() : valueText);
        }
    }
}
