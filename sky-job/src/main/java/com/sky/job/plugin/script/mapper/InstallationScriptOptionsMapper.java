package com.sky.job.plugin.script.mapper;

import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface InstallationScriptOptionsMapper {
    String lockInstallation(@Param("id") String id);
    void ensureOptions(@Param("id") String id);
    String lockOptions(@Param("id") String id);
    void saveOptions(@Param("id") String id, @Param("json") String json);
    List<String> variables(@Param("id") String id);
    void insertVariable(@Param("id") String id, @Param("variableId") String variableId, @Param("nameKey") String nameKey, @Param("json") String json);
    void updateVariable(@Param("id") String id, @Param("variableId") String variableId, @Param("nameKey") String nameKey, @Param("json") String json);
    void deleteVariable(@Param("id") String id, @Param("variableId") String variableId);
    void deleteVariables(@Param("id") String id);
    void deleteOptions(@Param("id") String id);
    List<String> legacyJobs(@Param("id") String id);
}
