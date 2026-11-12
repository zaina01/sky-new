package com.sky.job.system.mapper;

import com.sky.job.system.Role;
import com.sky.job.security.RoleDataScopeGrant;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface RoleMapper {
    List<Role> list(
            @Param("roleName") String roleName,
            @Param("roleNameLike") String roleNameLike,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long count(@Param("roleName") String roleName, @Param("roleNameLike") String roleNameLike);

    Role findById(@Param("roleId") Long roleId);

    Role findByName(@Param("roleName") String roleName);

    int countByName(@Param("roleName") String roleName);

    int countByNameForOtherRole(@Param("roleName") String roleName, @Param("roleId") Long roleId);

    int insert(java.util.Map<String, Object> values);

    int update(@Param("roleId") Long roleId, @Param("roleName") String roleName, @Param("description") String description);

    int deleteRolePermissions(@Param("roleId") Long roleId);

    int delete(@Param("roleId") Long roleId);

    List<String> permissions(@Param("roleId") Long roleId);

    int assignPermission(@Param("roleId") Long roleId, @Param("permissionName") String permissionName);

    int countAssigned(@Param("roleId") Long roleId);

    int updateDataScope(@Param("roleId") Long roleId, @Param("dataScope") String dataScope);

    int deleteRoleDepartments(@Param("roleId") Long roleId);

    int assignDepartment(@Param("roleId") Long roleId, @Param("departmentId") Long departmentId);

    List<Long> departmentIds(@Param("roleId") Long roleId);

    List<RoleDataScopeGrant> dataScopeGrants(@Param("userId") String userId, @Param("permissionName") String permissionName);

    List<Long> customDepartmentIds(@Param("userId") String userId, @Param("permissionName") String permissionName);
}
