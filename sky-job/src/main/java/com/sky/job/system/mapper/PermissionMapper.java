package com.sky.job.system.mapper;

import com.sky.job.system.Permission;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

public interface PermissionMapper {
    List<Permission> list(
            @Param("permissionName") String permissionName,
            @Param("permissionNameLike") String permissionNameLike,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long count(@Param("permissionName") String permissionName, @Param("permissionNameLike") String permissionNameLike);

    List<String> existingNames(@Param("permissionNames") Set<String> permissionNames);
}
