package com.sky.job.notification.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import com.sky.job.security.EffectiveDataScope;

public interface NotificationChannelMapper {
    List<NotificationChannelRow> list(
            @Param("channelType") String channelType,
            @Param("enabled") Boolean enabled,
            @Param("channelId") String channelId,
            @Param("displayName") String displayName,
            @Param("displayNameLike") String displayNameLike,
            @Param("scope") EffectiveDataScope scope,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long count(
            @Param("channelType") String channelType,
            @Param("enabled") Boolean enabled,
            @Param("channelId") String channelId,
            @Param("displayName") String displayName,
            @Param("displayNameLike") String displayNameLike,
            @Param("scope") EffectiveDataScope scope
    );

    NotificationChannelRow findByChannelId(@Param("channelId") String channelId, @Param("scope") EffectiveDataScope scope);

    NotificationChannelRow findByChannelIdUnscoped(@Param("channelId") String channelId);

    int insert(
            @Param("channelId") String channelId,
            @Param("channelType") String channelType,
            @Param("displayName") String displayName,
            @Param("enabled") boolean enabled,
            @Param("configJson") String configJson,
            @Param("remark") String remark,
            @Param("ownerUserId") String ownerUserId,
            @Param("ownerDepartmentId") Long ownerDepartmentId,
            @Param("createdBy") String createdBy
    );

    int update(
            @Param("channelId") String channelId,
            @Param("channelType") String channelType,
            @Param("displayName") String displayName,
            @Param("enabled") boolean enabled,
            @Param("configJson") String configJson,
            @Param("remark") String remark,
            @Param("updatedBy") String updatedBy
    );

    int updateEnabled(@Param("channelId") String channelId, @Param("enabled") boolean enabled, @Param("updatedBy") String updatedBy);

    int delete(@Param("channelId") String channelId);

    List<NotificationChannelRow> listEnabledUnscoped();

    List<NotificationChannelRow> listByIdsUnscoped(@Param("channelIds") List<String> channelIds);

    int countBindings(@Param("channelId") String channelId);

    int transferOwnership(@Param("channelId") String channelId, @Param("ownerUserId") String ownerUserId, @Param("ownerDepartmentId") Long ownerDepartmentId, @Param("updatedBy") String updatedBy);
}
