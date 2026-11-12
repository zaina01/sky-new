package com.sky.job.scheduler.mapper;

import com.sky.job.common.mapper.StatusCountRow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.sky.job.security.EffectiveDataScope;

public interface PluginRuntimeMetadataMapper {
    PluginRuntimeRow findByInstallationId(@Param("installationId") String installationId);

    List<PluginRuntimeRow> listRuntimes(
            @Param("pluginId") String pluginId,
            @Param("state") String state,
            @Param("scope") EffectiveDataScope scope,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long countRuntimes(@Param("pluginId") String pluginId, @Param("state") String state, @Param("scope") EffectiveDataScope scope);

    List<StatusCountRow> countRuntimesByState(@Param("scope") EffectiveDataScope scope);

    List<PluginRuntimeRow> listOptions(
            @Param("keyword") String keyword,
            @Param("keywordLike") String keywordLike,
            @Param("state") String state,
            @Param("usage") String usage,
            @Param("scope") EffectiveDataScope scope,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long countOptions(
            @Param("keyword") String keyword,
            @Param("keywordLike") String keywordLike,
            @Param("state") String state,
            @Param("usage") String usage,
            @Param("scope") EffectiveDataScope scope
    );

    int updateLifecycle(
            @Param("installationId") String installationId,
            @Param("state") String state,
            @Param("runnerHost") String runnerHost,
            @Param("runnerPort") Integer runnerPort,
            @Param("workDir") String workDir,
            @Param("startedAt") LocalDateTime startedAt
    );

    int updateRemoteDebug(
            @Param("installationId") String installationId,
            @Param("enabled") boolean enabled,
            @Param("port") int port,
            @Param("suspend") boolean suspend
    );

    int stopStaleRunningRuntimes();

    int softDelete(@Param("installationId") String installationId);

    int insertInstallation(Map<String, Object> values);

    List<String> notificationChannelIds(@Param("installationId") String installationId);

    List<String> jobNotificationChannelIds(@Param("installationId") String installationId);

    int deleteNotificationChannels(@Param("installationId") String installationId);

    int insertNotificationChannel(@Param("installationId") String installationId, @Param("channelId") String channelId, @Param("createdBy") String createdBy);

    int transferOwnership(@Param("installationId") String installationId, @Param("ownerUserId") String ownerUserId, @Param("ownerDepartmentId") Long ownerDepartmentId);
}
