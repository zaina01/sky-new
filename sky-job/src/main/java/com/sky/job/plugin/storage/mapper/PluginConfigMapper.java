package com.sky.job.plugin.storage.mapper;

import org.apache.ibatis.annotations.Param;

public interface PluginConfigMapper {
    String findConfigJson(@Param("pluginId") String pluginId);

    int upsertConfigJson(@Param("pluginId") String pluginId, @Param("configJson") String configJson);

    int deleteByPluginId(@Param("pluginId") String pluginId);
}
