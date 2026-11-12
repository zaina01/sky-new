package com.sky.job.plugin.callback.mapper;

import com.sky.job.plugin.callback.PluginServicePermission;
import com.sky.job.security.EffectiveDataScope;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PluginServicePermissionMapper {
    int countEnabled(
            @Param("callerInstallationId") String callerInstallationId,
            @Param("targetInstallationId") String targetInstallationId,
            @Param("operation") String operation
    );

    List<String> authorizedTargetInstallationIds(
            @Param("callerInstallationId") String callerInstallationId,
            @Param("targetPluginId") String targetPluginId,
            @Param("operation") String operation
    );

    List<PluginServicePermission> list(
            @Param("callerInstallationId") String callerInstallationId,
            @Param("callerPluginId") String callerPluginId,
            @Param("targetInstallationId") String targetInstallationId,
            @Param("targetPluginId") String targetPluginId,
            @Param("operation") String operation,
            @Param("enabled") Boolean enabled,
            @Param("scope") EffectiveDataScope scope,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long count(
            @Param("callerInstallationId") String callerInstallationId,
            @Param("callerPluginId") String callerPluginId,
            @Param("targetInstallationId") String targetInstallationId,
            @Param("targetPluginId") String targetPluginId,
            @Param("operation") String operation,
            @Param("enabled") Boolean enabled,
            @Param("scope") EffectiveDataScope scope
    );

    PluginServicePermission findById(@Param("id") long id, @Param("scope") EffectiveDataScope scope);

    int upsert(
            @Param("callerInstallationId") String callerInstallationId,
            @Param("targetInstallationId") String targetInstallationId,
            @Param("operation") String operation,
            @Param("enabled") boolean enabled
    );

    int updateEnabled(@Param("id") long id, @Param("enabled") boolean enabled);

    int delete(@Param("id") long id);
}
