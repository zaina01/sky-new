package com.sky.job.notification.mapper;

import com.sky.job.security.EffectiveDataScope;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NotificationRuleMapper {
    List<NotificationRuleRow> list(
            @Param("eventType") String eventType,
            @Param("enabled") Boolean enabled,
            @Param("pluginInstallationId") String pluginInstallationId,
            @Param("jobId") String jobId,
            @Param("scope") EffectiveDataScope scope,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long count(@Param("eventType") String eventType, @Param("enabled") Boolean enabled,
               @Param("pluginInstallationId") String pluginInstallationId, @Param("jobId") String jobId,
               @Param("scope") EffectiveDataScope scope);

    NotificationRuleRow findByRuleId(@Param("ruleId") String ruleId, @Param("scope") EffectiveDataScope scope);
    NotificationRuleRow findByRuleIdUnscoped(@Param("ruleId") String ruleId);

    int insert(
            @Param("ruleId") String ruleId,
            @Param("displayName") String displayName,
            @Param("enabled") boolean enabled,
            @Param("eventType") String eventType,
            @Param("pluginInstallationId") String pluginInstallationId,
            @Param("jobId") String jobId,
            @Param("severity") String severity,
            @Param("remark") String remark,
            @Param("ownerUserId") String ownerUserId,
            @Param("ownerDepartmentId") Long ownerDepartmentId,
            @Param("createdBy") String createdBy
    );

    int update(
            @Param("ruleId") String ruleId,
            @Param("displayName") String displayName,
            @Param("enabled") boolean enabled,
            @Param("eventType") String eventType,
            @Param("pluginInstallationId") String pluginInstallationId,
            @Param("jobId") String jobId,
            @Param("severity") String severity,
            @Param("remark") String remark,
            @Param("updatedBy") String updatedBy
    );

    int updateEnabled(@Param("ruleId") String ruleId, @Param("enabled") boolean enabled, @Param("updatedBy") String updatedBy);
    int delete(@Param("ruleId") String ruleId);

    List<NotificationRuleRow> listEnabledCandidates(
            @Param("eventType") String eventType,
            @Param("pluginInstallationId") String pluginInstallationId,
            @Param("jobId") String jobId,
            @Param("severity") String severity
    );

    List<String> channelIds(@Param("ruleId") String ruleId);
    int deleteChannels(@Param("ruleId") String ruleId);
    int insertChannel(@Param("ruleId") String ruleId, @Param("channelId") String channelId, @Param("createdBy") String createdBy);
    int transferOwnership(@Param("ruleId") String ruleId, @Param("ownerUserId") String ownerUserId, @Param("ownerDepartmentId") Long ownerDepartmentId, @Param("updatedBy") String updatedBy);
}
