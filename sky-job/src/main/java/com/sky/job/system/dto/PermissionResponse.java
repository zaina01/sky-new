package com.sky.job.system.dto;

import com.sky.job.system.Permission;

public record PermissionResponse(
        Long permissionId,
        String permissionName,
        String description
) {
    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(permission.permissionId(), permission.permissionName(), permission.description());
    }
}
