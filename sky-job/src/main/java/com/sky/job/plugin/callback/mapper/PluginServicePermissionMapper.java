package com.sky.job.plugin.callback.mapper;

import com.sky.job.plugin.callback.PluginServicePermission;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PluginServicePermissionMapper {
    int countEnabled(
            @Param("callerPluginId") String callerPluginId,
            @Param("targetPluginId") String targetPluginId,
            @Param("operation") String operation
    );

    List<PluginServicePermission> list(
            @Param("callerPluginId") String callerPluginId,
            @Param("targetPluginId") String targetPluginId,
            @Param("operation") String operation,
            @Param("enabled") Boolean enabled,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long count(
            @Param("callerPluginId") String callerPluginId,
            @Param("targetPluginId") String targetPluginId,
            @Param("operation") String operation,
            @Param("enabled") Boolean enabled
    );

    PluginServicePermission findById(@Param("id") long id);

    int upsert(
            @Param("callerPluginId") String callerPluginId,
            @Param("targetPluginId") String targetPluginId,
            @Param("operation") String operation,
            @Param("enabled") boolean enabled
    );

    int updateEnabled(@Param("id") long id, @Param("enabled") boolean enabled);

    int delete(@Param("id") long id);
}
