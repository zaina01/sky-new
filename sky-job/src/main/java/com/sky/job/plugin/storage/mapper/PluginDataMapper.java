package com.sky.job.plugin.storage.mapper;

import org.apache.ibatis.annotations.Param;

public interface PluginDataMapper {
    int countByKey(
            @Param("pluginId") String pluginId,
            @Param("collection") String collection,
            @Param("dataKey") String dataKey
    );

    int countRows(@Param("pluginId") String pluginId);

    int upsert(
            @Param("pluginId") String pluginId,
            @Param("collection") String collection,
            @Param("dataKey") String dataKey,
            @Param("dataJson") String dataJson
    );

    int delete(
            @Param("pluginId") String pluginId,
            @Param("collection") String collection,
            @Param("dataKey") String dataKey
    );

    int deleteByPluginId(@Param("pluginId") String pluginId);

    String select(
            @Param("pluginId") String pluginId,
            @Param("collection") String collection,
            @Param("dataKey") String dataKey
    );

    String selectAll(
            @Param("pluginId") String pluginId,
            @Param("collection") String collection,
            @Param("limit") int limit
    );
}
