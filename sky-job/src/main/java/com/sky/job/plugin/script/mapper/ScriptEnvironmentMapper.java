package com.sky.job.plugin.script.mapper;

import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface ScriptEnvironmentMapper {
    String get(@Param("key") String key);
    List<String> list(@Param("prefix") String prefix);
    void put(@Param("key") String key, @Param("json") String json);
}
