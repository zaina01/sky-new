package com.sky.job.system.dto;

import com.sky.job.system.Role;
import com.sky.job.security.DataScopeType;

import java.time.LocalDateTime;
import java.util.Set;

public record RoleResponse(
        Long roleId,
        String roleName,
        String description,
        DataScopeType dataScope,
        Set<Long> departmentIds,
        LocalDateTime createdAt,
        Set<String> permissions
) {
    public static RoleResponse from(Role role, Set<String> permissions, Set<Long> departmentIds) {
        return new RoleResponse(
                role.roleId(),
                role.roleName(),
                role.description(),
                role.dataScope(),
                departmentIds,
                role.createdAt(),
                permissions
        );
    }
}
