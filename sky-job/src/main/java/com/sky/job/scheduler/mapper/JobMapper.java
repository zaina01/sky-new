package com.sky.job.scheduler.mapper;

import com.sky.job.common.mapper.StatusCountRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface JobMapper {
    int insert(Map<String, Object> values);

    int update(
            @Param("id") long id,
            @Param("jobName") String jobName,
            @Param("pluginId") String pluginId,
            @Param("cronExpression") String cronExpression,
            @Param("misfirePolicy") String misfirePolicy,
            @Param("concurrent") boolean concurrent,
            @Param("inputJson") String inputJson,
            @Param("timeoutMillis") long timeoutMillis,
            @Param("remark") String remark,
            @Param("actor") String actor
    );

    ScheduledJobRow findById(@Param("id") long id);

    List<ScheduledJobRow> list(@Param("limit") int limit, @Param("offset") int offset);

    long count();

    long countByPluginId(@Param("pluginId") String pluginId);

    long countByStatus(@Param("status") String status);

    List<StatusCountRow> countByJobStatus();

    List<StatusCountRow> countByExecutedStatus();

    int delete(@Param("id") long id);

    int updateStatus(@Param("id") long id, @Param("status") String status, @Param("actor") String actor);

    int updateExecutedStatus(@Param("id") long id, @Param("status") String status);

    int markRunningIfIdle(@Param("id") long id, @Param("runningStatus") String runningStatus, @Param("idleStatus") String idleStatus);

    int markIdleIfRunning(@Param("id") long id, @Param("idleStatus") String idleStatus, @Param("runningStatus") String runningStatus);

    int resetRunningToIdle(@Param("idleStatus") String idleStatus, @Param("runningStatus") String runningStatus);

    List<ScheduledJobRow> findEnabledJobs(@Param("enabledStatus") String enabledStatus);
}
