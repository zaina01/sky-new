package com.sky.job.scheduler.mapper;

import com.sky.job.common.mapper.StatusCountRow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PluginRuntimeMetadataMapper {
    PluginRuntimeRow findByPluginId(@Param("pluginId") String pluginId);

    List<PluginRuntimeRow> listRuntimes(
            @Param("pluginId") String pluginId,
            @Param("state") String state,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long countRuntimes(@Param("pluginId") String pluginId, @Param("state") String state);

    List<StatusCountRow> countRuntimesByState();

    List<PluginRuntimeRow> listOptions(
            @Param("keyword") String keyword,
            @Param("keywordLike") String keywordLike,
            @Param("state") String state,
            @Param("usage") String usage,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long countOptions(
            @Param("keyword") String keyword,
            @Param("keywordLike") String keywordLike,
            @Param("state") String state,
            @Param("usage") String usage
    );

    int updateLifecycle(
            @Param("pluginId") String pluginId,
            @Param("state") String state,
            @Param("runnerHost") String runnerHost,
            @Param("runnerPort") Integer runnerPort,
            @Param("workDir") String workDir,
            @Param("startedAt") LocalDateTime startedAt
    );

    int updateRemoteDebug(
            @Param("pluginId") String pluginId,
            @Param("enabled") boolean enabled,
            @Param("port") int port,
            @Param("suspend") boolean suspend
    );

    int stopStaleRunningRuntimes();

    int deleteByPluginId(@Param("pluginId") String pluginId);
}
