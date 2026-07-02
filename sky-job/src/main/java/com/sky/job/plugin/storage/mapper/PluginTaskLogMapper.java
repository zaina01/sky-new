package com.sky.job.plugin.storage.mapper;

import com.sky.job.plugin.storage.PluginTaskLog;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

public interface PluginTaskLogMapper {
    int insert(
            @Param("pluginId") String pluginId,
            @Param("instanceId") String instanceId,
            @Param("jobId") String jobId,
            @Param("requestId") String requestId,
            @Param("level") String level,
            @Param("message") String message,
            @Param("sequenceNo") long sequenceNo,
            @Param("eventTime") Timestamp eventTime
    );

    List<PluginTaskLog> listByRequest(
            @Param("pluginId") String pluginId,
            @Param("requestId") String requestId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long countByRequest(@Param("pluginId") String pluginId, @Param("requestId") String requestId);

    long countOlderThan(@Param("cutoff") LocalDateTime cutoff);

    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
