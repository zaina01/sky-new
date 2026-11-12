package com.sky.job.plugin.storage.mapper;

import org.apache.ibatis.annotations.Param;

public interface PluginDataMapper {
    int countByKey(
            @Param("installationId") String installationId,
            @Param("collection") String collection,
            @Param("dataKey") String dataKey
    );

    int countRows(@Param("installationId") String installationId);

    int upsert(
            @Param("installationId") String installationId,
            @Param("collection") String collection,
            @Param("dataKey") String dataKey,
            @Param("dataJson") String dataJson
    );

    int delete(
            @Param("installationId") String installationId,
            @Param("collection") String collection,
            @Param("dataKey") String dataKey
    );

    int deleteByInstallationId(@Param("installationId") String installationId);

    String select(
            @Param("installationId") String installationId,
            @Param("collection") String collection,
            @Param("dataKey") String dataKey
    );

    String selectAll(
            @Param("installationId") String installationId,
            @Param("collection") String collection,
            @Param("limit") int limit
    );
}
