package com.sky.job.notification;

import com.sky.job.notification.mapper.NotificationChannelMapper;
import com.sky.job.notification.mapper.NotificationChannelRow;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import com.sky.job.security.EffectiveDataScope;

@Repository
public class NotificationChannelStore {
    private final NotificationChannelMapper mapper;

    public NotificationChannelStore(NotificationChannelMapper mapper) {
        this.mapper = mapper;
    }

    public List<NotificationChannel> list(NotificationChannelType channelType, Boolean enabled, String channelId, String displayName, EffectiveDataScope scope, Integer page, Integer size) {
        int normalizedPage = normalizedPage(page);
        int normalizedSize = normalizedSize(size);
        return mapper.list(channelType == null ? null : channelType.name(), enabled, trim(channelId), trim(displayName), like(displayName), scope, normalizedSize, (normalizedPage - 1) * normalizedSize).stream()
                .map(this::map)
                .toList();
    }

    public long count(NotificationChannelType channelType, Boolean enabled, String channelId, String displayName, EffectiveDataScope scope) {
        return mapper.count(channelType == null ? null : channelType.name(), enabled, trim(channelId), trim(displayName), like(displayName), scope);
    }

    public Optional<NotificationChannel> findByChannelId(String channelId, EffectiveDataScope scope) {
        return Optional.ofNullable(mapper.findByChannelId(channelId, scope)).map(this::map);
    }

    public Optional<NotificationChannel> findByChannelIdUnscoped(String channelId) {
        return Optional.ofNullable(mapper.findByChannelIdUnscoped(channelId)).map(this::map);
    }

    public void insert(String channelId, NotificationChannelType channelType, String displayName, boolean enabled, String configJson, String remark, String ownerUserId, Long ownerDepartmentId, String createdBy) {
        mapper.insert(channelId, channelType.name(), displayName, enabled, configJson, remark, ownerUserId, ownerDepartmentId, createdBy);
    }

    public int update(String channelId, NotificationChannelType channelType, String displayName, boolean enabled, String configJson, String remark, String updatedBy) {
        return mapper.update(channelId, channelType.name(), displayName, enabled, configJson, remark, updatedBy);
    }

    public int updateEnabled(String channelId, boolean enabled, String updatedBy) {
        return mapper.updateEnabled(channelId, enabled, updatedBy);
    }

    public int delete(String channelId) {
        return mapper.delete(channelId);
    }

    public List<NotificationChannel> listEnabledUnscoped() {
        return mapper.listEnabledUnscoped().stream().map(this::map).toList();
    }

    public List<NotificationChannel> listByIdsUnscoped(List<String> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return List.of();
        }
        return mapper.listByIdsUnscoped(channelIds).stream().map(this::map).toList();
    }

    public boolean hasBindings(String channelId) {
        return mapper.countBindings(channelId) > 0;
    }

    public int transferOwnership(String channelId, String ownerUserId, Long ownerDepartmentId, String actor) {
        return mapper.transferOwnership(channelId, ownerUserId, ownerDepartmentId, actor);
    }

    private NotificationChannel map(NotificationChannelRow row) {
        return new NotificationChannel(
                row.id(),
                row.channelId(),
                NotificationChannelType.valueOf(row.channelType()),
                row.displayName(),
                row.enabled(),
                row.configJson(),
                row.remark(),
                row.ownerUserId(),
                row.ownerUsername(),
                row.ownerDepartmentId(),
                row.ownerDepartmentName(),
                row.createdBy(),
                row.updatedBy(),
                row.createdAt(),
                row.updatedAt()
        );
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String like(String value) {
        return value == null || value.isBlank() ? null : "%" + value.trim() + "%";
    }

    private int normalizedPage(Integer page) {
        return Math.max(page == null ? 1 : page, 1);
    }

    private int normalizedSize(Integer size) {
        return Math.min(Math.max(size == null ? 20 : size, 1), 100);
    }
}
