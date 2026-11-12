package com.sky.job.plugin.storage.mapper;

import org.apache.ibatis.annotations.Param;

public interface PluginConfigMapper {
    String findConfigJson(@Param("installationId") String installationId);

    int upsertConfigJson(@Param("installationId") String installationId, @Param("configJson") String configJson);

    int deleteByInstallationId(@Param("installationId") String installationId);
}
