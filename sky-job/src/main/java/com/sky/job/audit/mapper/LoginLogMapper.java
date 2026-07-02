package com.sky.job.audit.mapper;

import com.sky.job.audit.LoginLog;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LoginLogMapper {
    int insert(
            @Param("userId") String userId,
            @Param("username") String username,
            @Param("status") String status,
            @Param("deviceType") String deviceType,
            @Param("loginIp") String loginIp,
            @Param("ipType") String ipType,
            @Param("userAgent") String userAgent,
            @Param("failReason") String failReason,
            @Param("requestUri") String requestUri,
            @Param("requestMethod") String requestMethod
    );

    List<LoginLog> list(
            @Param("username") String username,
            @Param("usernameLike") String usernameLike,
            @Param("userId") String userId,
            @Param("status") String status,
            @Param("loginIp") String loginIp,
            @Param("deviceType") String deviceType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long count(
            @Param("username") String username,
            @Param("usernameLike") String usernameLike,
            @Param("userId") String userId,
            @Param("status") String status,
            @Param("loginIp") String loginIp,
            @Param("deviceType") String deviceType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    long countOlderThan(@Param("cutoff") LocalDateTime cutoff);

    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
