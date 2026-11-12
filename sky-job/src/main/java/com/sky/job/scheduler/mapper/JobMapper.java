package com.sky.job.scheduler.mapper;

import com.sky.job.common.mapper.StatusCountRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import com.sky.job.security.EffectiveDataScope;

public interface JobMapper {
    int insert(Map<String, Object> values);

    int update(
            @Param("id") long id,
            @Param("jobName") String jobName,
            @Param("pluginInstallationId") String pluginInstallationId,
            @Param("cronExpression") String cronExpression,
            @Param("misfirePolicy") String misfirePolicy,
            @Param("concurrent") boolean concurrent,
            @Param("inputJson") String inputJson,
            @Param("timeoutMillis") long timeoutMillis,
            @Param("remark") String remark,
            @Param("actor") String actor
    );

    ScheduledJobRow findById(@Param("id") long id, @Param("scope") EffectiveDataScope scope);

    ScheduledJobRow findByIdUnscoped(@Param("id") long id);

    List<ScheduledJobRow> list(@Param("scope") EffectiveDataScope scope, @Param("limit") int limit, @Param("offset") int offset);

    long count(@Param("scope") EffectiveDataScope scope);

    long countByPluginInstallationId(@Param("pluginInstallationId") String pluginInstallationId);

    long countByStatus(@Param("status") String status, @Param("scope") EffectiveDataScope scope);

    List<StatusCountRow> countByJobStatus(@Param("scope") EffectiveDataScope scope);

    List<StatusCountRow> countByExecutedStatus(@Param("scope") EffectiveDataScope scope);

    int delete(@Param("id") long id);

    int updateStatus(@Param("id") long id, @Param("status") String status, @Param("actor") String actor);

    int updateExecutedStatus(@Param("id") long id, @Param("status") String status);

    int markRunningIfIdle(@Param("id") long id, @Param("runningStatus") String runningStatus, @Param("idleStatus") String idleStatus);

    int markIdleIfRunning(@Param("id") long id, @Param("idleStatus") String idleStatus, @Param("runningStatus") String runningStatus);

    int resetRunningToIdle(@Param("idleStatus") String idleStatus, @Param("runningStatus") String runningStatus);

    List<ScheduledJobRow> findEnabledJobs(@Param("enabledStatus") String enabledStatus);

    List<String> notificationChannelIds(@Param("jobId") long jobId);

    int deleteNotificationChannels(@Param("jobId") long jobId);

    int insertNotificationChannel(@Param("jobId") long jobId, @Param("channelId") String channelId, @Param("createdBy") String createdBy);

    int transferOwnership(@Param("id") long id, @Param("ownerUserId") String ownerUserId, @Param("ownerDepartmentId") Long ownerDepartmentId, @Param("actor") String actor);
}
