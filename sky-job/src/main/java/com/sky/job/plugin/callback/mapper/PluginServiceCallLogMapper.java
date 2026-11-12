package com.sky.job.plugin.callback.mapper;

import com.sky.job.plugin.callback.PluginServiceCallLog;
import com.sky.job.plugin.callback.PluginServiceCallLogCommand;
import com.sky.job.security.EffectiveDataScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PluginServiceCallLogMapper {
    int insert(PluginServiceCallLogCommand command);

    PluginServiceCallLog findById(@Param("id") long id, @Param("scope") EffectiveDataScope scope);

    List<PluginServiceCallLog> list(
            @Param("jobId") Long jobId,
            @Param("callerInstallationId") String callerInstallationId,
            @Param("callerPluginId") String callerPluginId,
            @Param("targetInstallationId") String targetInstallationId,
            @Param("targetPluginId") String targetPluginId,
            @Param("operation") String operation,
            @Param("success") Boolean success,
            @Param("scope") EffectiveDataScope scope,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long count(
            @Param("jobId") Long jobId,
            @Param("callerInstallationId") String callerInstallationId,
            @Param("callerPluginId") String callerPluginId,
            @Param("targetInstallationId") String targetInstallationId,
            @Param("targetPluginId") String targetPluginId,
            @Param("operation") String operation,
            @Param("success") Boolean success,
            @Param("scope") EffectiveDataScope scope
    );
}
