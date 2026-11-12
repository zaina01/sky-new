package com.sky.job.system.dto;

import com.sky.job.security.DataScopeType;

import java.util.Set;

public record ReplaceRoleDataScopeRequest(
        DataScopeType dataScope,
        Set<Long> departmentIds
) {
}
