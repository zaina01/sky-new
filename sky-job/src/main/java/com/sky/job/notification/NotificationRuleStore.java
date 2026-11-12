package com.sky.job.notification;

import com.sky.job.notification.mapper.NotificationRuleMapper;
import com.sky.job.notification.mapper.NotificationRuleRow;
import com.sky.job.security.EffectiveDataScope;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class NotificationRuleStore {
    private final NotificationRuleMapper mapper;

    public NotificationRuleStore(NotificationRuleMapper mapper) {
        this.mapper = mapper;
    }

    public List<NotificationRule> list(NotificationEventType eventType, Boolean enabled, String installationId, String jobId, EffectiveDataScope scope, Integer page, Integer size) {
        int p = normalizedPage(page);
        int s = normalizedSize(size);
        return mapper.list(eventType == null ? null : eventType.name(), enabled, trim(installationId), trim(jobId), scope, s, (p - 1) * s).stream().map(this::map).toList();
    }

    public long count(NotificationEventType eventType, Boolean enabled, String installationId, String jobId, EffectiveDataScope scope) {
        return mapper.count(eventType == null ? null : eventType.name(), enabled, trim(installationId), trim(jobId), scope);
    }

    public Optional<NotificationRule> findByRuleId(String ruleId, EffectiveDataScope scope) {
        return Optional.ofNullable(mapper.findByRuleId(ruleId, scope)).map(this::map);
    }

    public Optional<NotificationRule> findByRuleIdUnscoped(String ruleId) {
        return Optional.ofNullable(mapper.findByRuleIdUnscoped(ruleId)).map(this::map);
    }

    public void insert(String ruleId, String displayName, boolean enabled, NotificationEventType eventType,
                       String installationId, String jobId, String severity, List<String> channelIds,
                       String remark, String ownerUserId, Long ownerDepartmentId, String createdBy) {
        mapper.insert(ruleId, displayName, enabled, eventType.name(), value(installationId), value(jobId), severity,
                remark, ownerUserId, ownerDepartmentId, createdBy);
        replaceChannels(ruleId, channelIds, createdBy);
    }

    public int update(String ruleId, String displayName, boolean enabled, NotificationEventType eventType,
                      String installationId, String jobId, String severity, List<String> channelIds,
                      String remark, String updatedBy) {
        int updated = mapper.update(ruleId, displayName, enabled, eventType.name(), value(installationId), value(jobId), severity, remark, updatedBy);
        if (updated > 0) replaceChannels(ruleId, channelIds, updatedBy);
        return updated;
    }

    public int updateEnabled(String ruleId, boolean enabled, String updatedBy) {
        return mapper.updateEnabled(ruleId, enabled, updatedBy);
    }

    public int delete(String ruleId) {
        mapper.deleteChannels(ruleId);
        return mapper.delete(ruleId);
    }

    public List<NotificationRule> listEnabledCandidates(NotificationEventType eventType, String installationId, String jobId, String severity) {
        return mapper.listEnabledCandidates(eventType.name(), value(installationId), value(jobId), value(severity)).stream().map(this::map).toList();
    }

    public void replaceChannels(String ruleId, List<String> channelIds, String actor) {
        mapper.deleteChannels(ruleId);
        for (String channelId : channelIds == null ? List.<String>of() : channelIds) mapper.insertChannel(ruleId, channelId, actor);
    }

    public int transferOwnership(String ruleId, String ownerUserId, Long ownerDepartmentId, String actor) {
        return mapper.transferOwnership(ruleId, ownerUserId, ownerDepartmentId, actor);
    }

    private NotificationRule map(NotificationRuleRow row) {
        return new NotificationRule(
                row.id(), row.ruleId(), row.displayName(), row.enabled(), NotificationEventType.valueOf(row.eventType()),
                value(row.pluginInstallationId()), value(row.pluginId()), value(row.jobId()), value(row.severity()),
                mapper.channelIds(row.ruleId()), row.remark(), row.ownerUserId(), row.ownerUsername(),
                row.ownerDepartmentId(), row.ownerDepartmentName(), row.createdBy(), row.updatedBy(), row.createdAt(), row.updatedAt()
        );
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String value(String value) { return value == null ? "" : value; }
    private int normalizedPage(Integer page) { return Math.max(page == null ? 1 : page, 1); }
    private int normalizedSize(Integer size) { return Math.min(Math.max(size == null ? 20 : size, 1), 100); }
}
