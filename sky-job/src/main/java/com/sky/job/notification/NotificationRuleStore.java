package com.sky.job.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.notification.mapper.NotificationRuleMapper;
import com.sky.job.notification.mapper.NotificationRuleRow;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class NotificationRuleStore {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final NotificationRuleMapper mapper;
    private final ObjectMapper objectMapper;

    public NotificationRuleStore(NotificationRuleMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public List<NotificationRule> list(NotificationEventType eventType, Boolean enabled, String pluginId, String jobId, Integer page, Integer size) {
        int normalizedPage = normalizedPage(page);
        int normalizedSize = normalizedSize(size);
        return mapper.list(eventType == null ? null : eventType.name(), enabled, trim(pluginId), trim(jobId), normalizedSize, (normalizedPage - 1) * normalizedSize).stream()
                .map(this::map)
                .toList();
    }

    public long count(NotificationEventType eventType, Boolean enabled, String pluginId, String jobId) {
        return mapper.count(eventType == null ? null : eventType.name(), enabled, trim(pluginId), trim(jobId));
    }

    public Optional<NotificationRule> findByRuleId(String ruleId) {
        return Optional.ofNullable(mapper.findByRuleId(ruleId)).map(this::map);
    }

    public void insert(String ruleId, String displayName, boolean enabled, NotificationEventType eventType, String pluginId, String jobId, String severity, List<String> channelIds, String remark, String createdBy) {
        mapper.insert(ruleId, displayName, enabled, eventType.name(), pluginId, jobId, severity, json(channelIds), remark, createdBy);
    }

    public int update(String ruleId, String displayName, boolean enabled, NotificationEventType eventType, String pluginId, String jobId, String severity, List<String> channelIds, String remark, String updatedBy) {
        return mapper.update(ruleId, displayName, enabled, eventType.name(), pluginId, jobId, severity, json(channelIds), remark, updatedBy);
    }

    public int updateEnabled(String ruleId, boolean enabled, String updatedBy) {
        return mapper.updateEnabled(ruleId, enabled, updatedBy);
    }

    public int delete(String ruleId) {
        return mapper.delete(ruleId);
    }

    public List<NotificationRule> listEnabledCandidates(NotificationEventType eventType, String pluginId, String jobId, String severity) {
        return mapper.listEnabledCandidates(eventType.name(), value(pluginId), value(jobId), value(severity)).stream()
                .map(this::map)
                .toList();
    }

    private NotificationRule map(NotificationRuleRow row) {
        return new NotificationRule(
                row.id(),
                row.ruleId(),
                row.displayName(),
                row.enabled(),
                NotificationEventType.valueOf(row.eventType()),
                row.pluginId(),
                row.jobId(),
                row.severity(),
                channelIds(row.channelIdsJson()),
                row.remark(),
                row.createdBy(),
                row.updatedBy(),
                row.createdAt(),
                row.updatedAt()
        );
    }

    private String json(List<String> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize notification rule channel ids", exception);
        }
    }

    private List<String> channelIds(String json) {
        try {
            return objectMapper.readValue(json == null || json.isBlank() ? "[]" : json, STRING_LIST);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid notification rule channel ids", exception);
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int normalizedPage(Integer page) {
        return Math.max(page == null ? 1 : page, 1);
    }

    private int normalizedSize(Integer size) {
        return Math.min(Math.max(size == null ? 20 : size, 1), 100);
    }
}
