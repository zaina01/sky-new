package com.sky.job.plugin.storage.mapper;

import com.sky.job.plugin.storage.PluginAuditLog;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PluginAuditLogMapper {
    int insert(
            @Param("installationId") String installationId,
            @Param("pluginId") String pluginId,
            @Param("instanceId") String instanceId,
            @Param("actor") String actor,
            @Param("action") String action,
            @Param("detailJson") String detailJson
    );

    List<PluginAuditLog> list(
            @Param("installationId") String installationId,
            @Param("pluginId") String pluginId,
            @Param("instanceId") String instanceId,
            @Param("actor") String actor,
            @Param("actorLike") String actorLike,
            @Param("action") String action,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("scope") com.sky.job.security.EffectiveDataScope scope,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long count(
            @Param("installationId") String installationId,
            @Param("pluginId") String pluginId,
            @Param("instanceId") String instanceId,
            @Param("actor") String actor,
            @Param("actorLike") String actorLike,
            @Param("action") String action,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("scope") com.sky.job.security.EffectiveDataScope scope
    );

    long countOlderThan(@Param("cutoff") LocalDateTime cutoff);

    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
