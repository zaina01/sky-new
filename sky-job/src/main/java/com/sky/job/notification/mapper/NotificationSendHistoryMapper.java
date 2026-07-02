package com.sky.job.notification.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface NotificationSendHistoryMapper {
    int insertPending(Map<String, Object> values);

    int restoreStaleSending(
            @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("retryWaitingStatus") String retryWaitingStatus,
            @Param("sendingStatus") String sendingStatus,
            @Param("errorType") String errorType,
            @Param("errorMessage") String errorMessage
    );

    List<Long> listDueIds(
            @Param("pendingStatus") String pendingStatus,
            @Param("retryWaitingStatus") String retryWaitingStatus,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    int claimDue(
            @Param("id") long id,
            @Param("sendingStatus") String sendingStatus,
            @Param("workerId") String workerId,
            @Param("now") LocalDateTime now,
            @Param("pendingStatus") String pendingStatus,
            @Param("retryWaitingStatus") String retryWaitingStatus
    );

    int markSendingSuccess(
            @Param("id") long id,
            @Param("successStatus") String successStatus,
            @Param("providerStatusCode") String providerStatusCode,
            @Param("providerMessage") String providerMessage
    );

    int markSendingRetry(
            @Param("id") long id,
            @Param("retryWaitingStatus") String retryWaitingStatus,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("errorType") String errorType,
            @Param("errorMessage") String errorMessage,
            @Param("providerStatusCode") String providerStatusCode,
            @Param("providerMessage") String providerMessage
    );

    int markSendingFinalFailed(
            @Param("id") long id,
            @Param("failedStatus") String failedStatus,
            @Param("errorType") String errorType,
            @Param("errorMessage") String errorMessage,
            @Param("providerStatusCode") String providerStatusCode,
            @Param("providerMessage") String providerMessage
    );

    int requestManualRetry(
            @Param("id") long id,
            @Param("pendingStatus") String pendingStatus,
            @Param("failedStatus") String failedStatus,
            @Param("actor") String actor,
            @Param("now") LocalDateTime now
    );

    int markSkipped(@Param("id") long id, @Param("skippedStatus") String skippedStatus, @Param("message") String message);

    List<NotificationSendHistoryRow> list(
            @Param("sourceType") String sourceType,
            @Param("channelId") String channelId,
            @Param("channelType") String channelType,
            @Param("status") String status,
            @Param("pluginId") String pluginId,
            @Param("jobId") String jobId,
            @Param("executionId") Long executionId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long count(
            @Param("sourceType") String sourceType,
            @Param("channelId") String channelId,
            @Param("channelType") String channelType,
            @Param("status") String status,
            @Param("pluginId") String pluginId,
            @Param("jobId") String jobId,
            @Param("executionId") Long executionId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    NotificationSendHistoryRow findById(@Param("id") long id);

    long countOlderThan(@Param("cutoff") LocalDateTime cutoff);

    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
