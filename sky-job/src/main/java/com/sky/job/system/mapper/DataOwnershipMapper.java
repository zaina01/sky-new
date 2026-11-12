package com.sky.job.system.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DataOwnershipMapper {
    List<Long> jobIdsByInstallationId(@Param("installationId") String installationId);

    List<String> ruleIdsByInstallationId(@Param("installationId") String installationId);

    List<String> servicePeerInstallationIds(@Param("installationId") String installationId);

    List<String> installationIdsByChannelId(@Param("channelId") String channelId);

    List<Long> jobIdsByChannelId(@Param("channelId") String channelId);

    List<String> ruleIdsByChannelId(@Param("channelId") String channelId);

    List<String> ruleIdsByJobId(@Param("jobId") long jobId);
}
