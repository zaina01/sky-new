package com.sky.job.plugin.storage.mapper;

import com.sky.job.plugin.storage.PluginAccount;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PluginAccountMapper {
    List<PluginAccount> listByPluginId(@Param("pluginId") String pluginId);

    List<PluginAccount> listEnabledByPluginId(@Param("pluginId") String pluginId);

    PluginAccount getRecord(@Param("pluginId") String pluginId, @Param("accountId") String accountId);

    PluginAccount getEnabledRecord(@Param("pluginId") String pluginId, @Param("accountId") String accountId);

    List<PluginAccount> listManaged(
            @Param("pluginId") String pluginId,
            @Param("enabled") Boolean enabled,
            @Param("accountId") String accountId,
            @Param("accountIdLike") String accountIdLike,
            @Param("displayName") String displayName,
            @Param("displayNameLike") String displayNameLike,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    List<PluginAccount> listManagedForExport(
            @Param("pluginId") String pluginId,
            @Param("enabled") Boolean enabled,
            @Param("accountId") String accountId,
            @Param("accountIdLike") String accountIdLike,
            @Param("displayName") String displayName,
            @Param("displayNameLike") String displayNameLike,
            @Param("limit") int limit
    );

    long countManaged(
            @Param("pluginId") String pluginId,
            @Param("enabled") Boolean enabled,
            @Param("accountId") String accountId,
            @Param("accountIdLike") String accountIdLike,
            @Param("displayName") String displayName,
            @Param("displayNameLike") String displayNameLike
    );

    int upsert(
            @Param("pluginId") String pluginId,
            @Param("accountId") String accountId,
            @Param("displayName") String displayName,
            @Param("accountJson") String accountJson,
            @Param("enabled") boolean enabled
    );

    int updateEnabled(
            @Param("pluginId") String pluginId,
            @Param("accountId") String accountId,
            @Param("enabled") boolean enabled
    );

    int delete(@Param("pluginId") String pluginId, @Param("accountId") String accountId);

    int deleteByPluginId(@Param("pluginId") String pluginId);
}
