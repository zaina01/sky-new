package com.sky.job.security;

public record RoleDataScopeGrant(
        long roleId,
        String roleName,
        DataScopeType dataScope
) {
}
