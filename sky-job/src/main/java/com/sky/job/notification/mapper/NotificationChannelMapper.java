package com.sky.job.notification.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NotificationChannelMapper {
    List<NotificationChannelRow> list(
            @Param("channelType") String channelType,
            @Param("enabled") Boolean enabled,
            @Param("channelId") String channelId,
            @Param("displayName") String displayName,
            @Param("displayNameLike") String displayNameLike,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long count(
            @Param("channelType") String channelType,
            @Param("enabled") Boolean enabled,
            @Param("channelId") String channelId,
            @Param("displayName") String displayName,
            @Param("displayNameLike") String displayNameLike
    );

    NotificationChannelRow findByChannelId(@Param("channelId") String channelId);

    int insert(
            @Param("channelId") String channelId,
            @Param("channelType") String channelType,
            @Param("displayName") String displayName,
            @Param("enabled") boolean enabled,
            @Param("configJson") String configJson,
            @Param("remark") String remark,
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

    List<NotificationChannelRow> listEnabled();

    List<NotificationChannelRow> listEnabledByIds(@Param("channelIds") List<String> channelIds);
}
