package com.sky.job.plugin.storage.mapper;

import com.sky.job.plugin.storage.PluginAccount;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PluginAccountMapper {
    List<PluginAccount> listByInstallationId(@Param("installationId") String installationId);

    List<PluginAccount> listEnabledByInstallationId(@Param("installationId") String installationId);

    PluginAccount getRecord(@Param("installationId") String installationId, @Param("accountId") String accountId);

    PluginAccount getEnabledRecord(@Param("installationId") String installationId, @Param("accountId") String accountId);

    List<PluginAccount> listManaged(
            @Param("installationId") String installationId,
            @Param("enabled") Boolean enabled,
            @Param("accountId") String accountId,
            @Param("accountIdLike") String accountIdLike,
            @Param("displayName") String displayName,
            @Param("displayNameLike") String displayNameLike,
            @Param("groupId") Long groupId,
            @Param("ungrouped") boolean ungrouped,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    List<PluginAccount> listManagedForExport(
            @Param("installationId") String installationId,
            @Param("enabled") Boolean enabled,
            @Param("accountId") String accountId,
            @Param("accountIdLike") String accountIdLike,
            @Param("displayName") String displayName,
            @Param("displayNameLike") String displayNameLike,
            @Param("groupId") Long groupId,
            @Param("ungrouped") boolean ungrouped,
            @Param("limit") int limit
    );

    long countManaged(
            @Param("installationId") String installationId,
            @Param("enabled") Boolean enabled,
            @Param("accountId") String accountId,
            @Param("accountIdLike") String accountIdLike,
            @Param("displayName") String displayName,
            @Param("displayNameLike") String displayNameLike,
            @Param("groupId") Long groupId,
            @Param("ungrouped") boolean ungrouped
    );

    int upsert(
            @Param("installationId") String installationId,
            @Param("accountId") String accountId,
            @Param("displayName") String displayName,
            @Param("accountJson") String accountJson,
            @Param("enabled") boolean enabled
    );

    int updateEnabled(
            @Param("installationId") String installationId,
            @Param("accountId") String accountId,
            @Param("enabled") boolean enabled
    );

    int delete(@Param("installationId") String installationId, @Param("accountId") String accountId);

    int deleteByInstallationId(@Param("installationId") String installationId);

    int updateGroup(@Param("installationId") String installationId, @Param("accountId") String accountId,
                    @Param("groupId") Long groupId);

    int clearGroup(@Param("installationId") String installationId, @Param("groupId") long groupId);
}
