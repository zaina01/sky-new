package com.sky.job.system.mapper;

import com.sky.job.system.Department;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface DepartmentMapper {
    List<Department> list(@Param("departmentName") String departmentName, @Param("departmentNameLike") String departmentNameLike, @Param("enabled") Boolean enabled);

    Department findById(@Param("departmentId") Long departmentId);

    List<Long> descendantIds(@Param("departmentId") Long departmentId);

    int countByName(@Param("parentId") Long parentId, @Param("departmentName") String departmentName);

    int countByNameForOther(@Param("parentId") Long parentId, @Param("departmentName") String departmentName, @Param("departmentId") Long departmentId);

    int countChildren(@Param("departmentId") Long departmentId);

    int countUsers(@Param("departmentId") Long departmentId);

    int insert(Map<String, Object> values);

    int update(@Param("departmentId") Long departmentId, @Param("parentId") Long parentId, @Param("departmentName") String departmentName, @Param("sortOrder") int sortOrder, @Param("enabled") boolean enabled);

    int updateEnabled(@Param("departmentId") Long departmentId, @Param("enabled") boolean enabled);

    int softDelete(@Param("departmentId") Long departmentId);
}
