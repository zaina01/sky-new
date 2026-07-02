package com.sky.job.notification;

import com.sky.job.notification.mapper.NotificationChannelMapper;
import com.sky.job.notification.mapper.NotificationChannelRow;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class NotificationChannelStore {
    private final NotificationChannelMapper mapper;

    public NotificationChannelStore(NotificationChannelMapper mapper) {
        this.mapper = mapper;
    }

    public List<NotificationChannel> list(NotificationChannelType channelType, Boolean enabled, String channelId, String displayName, Integer page, Integer size) {
        int normalizedPage = normalizedPage(page);
        int normalizedSize = normalizedSize(size);
        return mapper.list(channelType == null ? null : channelType.name(), enabled, trim(channelId), trim(displayName), like(displayName), normalizedSize, (normalizedPage - 1) * normalizedSize).stream()
                .map(this::map)
                .toList();
    }

    public long count(NotificationChannelType channelType, Boolean enabled, String channelId, String displayName) {
        return mapper.count(channelType == null ? null : channelType.name(), enabled, trim(channelId), trim(displayName), like(displayName));
    }

    public Optional<NotificationChannel> findByChannelId(String channelId) {
        return Optional.ofNullable(mapper.findByChannelId(channelId)).map(this::map);
    }

    public void insert(String channelId, NotificationChannelType channelType, String displayName, boolean enabled, String configJson, String remark, String createdBy) {
        mapper.insert(channelId, channelType.name(), displayName, enabled, configJson, remark, createdBy);
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

    public List<NotificationChannel> listEnabled() {
        return mapper.listEnabled().stream().map(this::map).toList();
    }

    public List<NotificationChannel> listEnabledByIds(List<String> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return List.of();
        }
        return mapper.listEnabledByIds(channelIds).stream().map(this::map).toList();
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
