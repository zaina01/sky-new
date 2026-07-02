package com.sky.job.system.mapper;

import com.sky.job.system.SystemSetting;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

public interface SystemSettingMapper {
    List<SystemSetting> list(@Param("category") String category);

    List<SystemSetting> listAll();

    Optional<SystemSetting> findByKey(@Param("settingKey") String settingKey);

    int insertIfAbsent(
            @Param("settingKey") String settingKey,
            @Param("category") String category,
            @Param("valueText") String valueText,
            @Param("valueType") String valueType,
            @Param("description") String description,
            @Param("editable") boolean editable,
            @Param("updatedBy") String updatedBy
    );

    int updateValue(
            @Param("settingKey") String settingKey,
            @Param("valueText") String valueText,
            @Param("updatedBy") String updatedBy
    );
}
