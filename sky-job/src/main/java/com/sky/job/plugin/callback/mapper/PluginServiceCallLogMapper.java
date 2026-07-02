package com.sky.job.plugin.callback.mapper;

import com.sky.job.plugin.callback.PluginServiceCallLog;
import com.sky.job.plugin.callback.PluginServiceCallLogCommand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PluginServiceCallLogMapper {
    int insert(PluginServiceCallLogCommand command);

    PluginServiceCallLog findById(@Param("id") long id);

    List<PluginServiceCallLog> list(
            @Param("jobId") Long jobId,
            @Param("callerPluginId") String callerPluginId,
            @Param("targetPluginId") String targetPluginId,
            @Param("operation") String operation,
            @Param("success") Boolean success,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long count(
            @Param("jobId") Long jobId,
            @Param("callerPluginId") String callerPluginId,
            @Param("targetPluginId") String targetPluginId,
            @Param("operation") String operation,
            @Param("success") Boolean success
    );
}
