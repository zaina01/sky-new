package com.sky.job.audit.mapper;

import com.sky.job.audit.SystemLog;
import com.sky.job.security.EffectiveDataScope;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SystemLogMapper {
    int insert(
            @Param("module") String module,
            @Param("action") String action,
            @Param("level") String level,
            @Param("status") String status,
            @Param("operatorUserId") String operatorUserId,
            @Param("ownerDepartmentId") Long ownerDepartmentId,
            @Param("operatorName") String operatorName,
            @Param("sourceType") String sourceType,
            @Param("requestUri") String requestUri,
            @Param("requestMethod") String requestMethod,
            @Param("ip") String ip,
            @Param("userAgent") String userAgent,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("message") String message
    );

    int insertSystem(
            @Param("module") String module,
            @Param("action") String action,
            @Param("level") String level,
            @Param("status") String status,
            @Param("sourceType") String sourceType,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("message") String message
    );

    List<SystemLog> list(
            @Param("module") String module,
            @Param("action") String action,
            @Param("level") String level,
            @Param("status") String status,
            @Param("operatorUserId") String operatorUserId,
            @Param("operatorName") String operatorName,
            @Param("operatorNameLike") String operatorNameLike,
            @Param("sourceType") String sourceType,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("scope") EffectiveDataScope scope,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long count(
            @Param("module") String module,
            @Param("action") String action,
            @Param("level") String level,
            @Param("status") String status,
            @Param("operatorUserId") String operatorUserId,
            @Param("operatorName") String operatorName,
            @Param("operatorNameLike") String operatorNameLike,
            @Param("sourceType") String sourceType,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("scope") EffectiveDataScope scope
    );

    long countOlderThan(@Param("cutoff") LocalDateTime cutoff);

    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
