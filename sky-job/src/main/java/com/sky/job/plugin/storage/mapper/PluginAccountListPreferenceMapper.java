package com.sky.job.plugin.storage.mapper;

import org.apache.ibatis.annotations.Param;

public interface PluginAccountListPreferenceMapper {
    String findSelectedFieldsJson(
            @Param("userId") String userId,
            @Param("installationId") String installationId
    );

    int upsert(
            @Param("userId") String userId,
            @Param("installationId") String installationId,
            @Param("selectedFieldsJson") String selectedFieldsJson
    );

    int delete(
            @Param("userId") String userId,
            @Param("installationId") String installationId
    );

    int deleteByInstallationId(@Param("installationId") String installationId);
}
