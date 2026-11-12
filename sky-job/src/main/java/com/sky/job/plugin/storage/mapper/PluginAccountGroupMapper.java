package com.sky.job.plugin.storage.mapper;

import com.sky.job.plugin.storage.PluginAccountGroup;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface PluginAccountGroupMapper {
    List<PluginAccountGroup> list(@Param("installationId") String installationId);

    PluginAccountGroup find(@Param("installationId") String installationId, @Param("groupId") long groupId);

    PluginAccountGroup lock(@Param("installationId") String installationId, @Param("groupId") long groupId);

    int insert(Map<String, Object> values);

    int rename(@Param("installationId") String installationId, @Param("groupId") long groupId, @Param("name") String name);

    int delete(@Param("installationId") String installationId, @Param("groupId") long groupId);

    int deleteByInstallationId(@Param("installationId") String installationId);
}
