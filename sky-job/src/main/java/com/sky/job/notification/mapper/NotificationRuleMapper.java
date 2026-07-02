package com.sky.job.notification.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NotificationRuleMapper {
    List<NotificationRuleRow> list(
            @Param("eventType") String eventType,
            @Param("enabled") Boolean enabled,
            @Param("pluginId") String pluginId,
            @Param("jobId") String jobId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long count(
            @Param("eventType") String eventType,
            @Param("enabled") Boolean enabled,
            @Param("pluginId") String pluginId,
            @Param("jobId") String jobId
    );

    NotificationRuleRow findByRuleId(@Param("ruleId") String ruleId);

    int insert(
            @Param("ruleId") String ruleId,
            @Param("displayName") String displayName,
            @Param("enabled") boolean enabled,
            @Param("eventType") String eventType,
            @Param("pluginId") String pluginId,
            @Param("jobId") String jobId,
            @Param("severity") String severity,
            @Param("channelIdsJson") String channelIdsJson,
            @Param("remark") String remark,
            @Param("createdBy") String createdBy
    );

    int update(
            @Param("ruleId") String ruleId,
            @Param("displayName") String displayName,
            @Param("enabled") boolean enabled,
            @Param("eventType") String eventType,
            @Param("pluginId") String pluginId,
            @Param("jobId") String jobId,
            @Param("severity") String severity,
            @Param("channelIdsJson") String channelIdsJson,
            @Param("remark") String remark,
            @Param("updatedBy") String updatedBy
    );

    int updateEnabled(@Param("ruleId") String ruleId, @Param("enabled") boolean enabled, @Param("updatedBy") String updatedBy);

    int delete(@Param("ruleId") String ruleId);

    List<NotificationRuleRow> listEnabledCandidates(
            @Param("eventType") String eventType,
            @Param("pluginId") String pluginId,
            @Param("jobId") String jobId,
            @Param("severity") String severity
    );
}
