package com.sky.job.scheduler.mapper;

import com.sky.job.common.mapper.DailyStatusCountRow;
import com.sky.job.common.mapper.StatusCountRow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface JobExecutionMapper {
    int insertRunning(Map<String, Object> values);

    int updateRequestId(@Param("id") long id, @Param("requestId") String requestId);

    int markTerminal(
            @Param("id") long id,
            @Param("status") String status,
            @Param("message") String message,
            @Param("resultJson") String resultJson,
            @Param("errorType") String errorType,
            @Param("errorMessage") String errorMessage,
            @Param("finishedAt") LocalDateTime finishedAt,
            @Param("durationMillis") long durationMillis
    );

    int markTerminalIfRunning(
            @Param("id") long id,
            @Param("status") String status,
            @Param("runningStatus") String runningStatus,
            @Param("message") String message,
            @Param("resultJson") String resultJson,
            @Param("errorType") String errorType,
            @Param("errorMessage") String errorMessage,
            @Param("finishedAt") LocalDateTime finishedAt,
            @Param("durationMillis") long durationMillis
    );

    int markCancelledIfRunning(
            @Param("id") long id,
            @Param("cancelledStatus") String cancelledStatus,
            @Param("runningStatus") String runningStatus,
            @Param("message") String message,
            @Param("errorType") String errorType,
            @Param("finishedAt") LocalDateTime finishedAt,
            @Param("durationMillis") long durationMillis
    );

    int markSystemExitForRunning(@Param("systemExitStatus") String systemExitStatus, @Param("runningStatus") String runningStatus);

    long countOlderThan(@Param("cutoff") LocalDateTime cutoff, @Param("runningStatus") String runningStatus);

    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff, @Param("runningStatus") String runningStatus, @Param("limit") int limit);

    int countRunningByJobId(@Param("jobId") long jobId, @Param("runningStatus") String runningStatus);

    int deleteByJobId(@Param("jobId") long jobId);

    JobExecutionRow findById(@Param("id") long id);

    List<JobExecutionRow> list(
            @Param("jobId") Long jobId,
            @Param("pluginId") String pluginId,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long count(@Param("jobId") Long jobId, @Param("pluginId") String pluginId, @Param("status") String status);

    List<StatusCountRow> countByStatusBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    List<DailyStatusCountRow> countDailyByStatus(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    List<JobExecutionRow> listRecentFailures(@Param("statuses") List<String> statuses, @Param("limit") int limit);
}
