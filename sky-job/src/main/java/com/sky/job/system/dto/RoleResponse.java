package com.sky.job.system.dto;

import com.sky.job.system.Role;

import java.time.LocalDateTime;
import java.util.Set;

public record RoleResponse(
        Long roleId,
        String roleName,
        String description,
        LocalDateTime createdAt,
        Set<String> permissions
) {
    public static RoleResponse from(Role role, Set<String> permissions) {
        return new RoleResponse(
                role.roleId(),
                role.roleName(),
                role.description(),
                role.createdAt(),
                permissions
        );
    }
}
