package com.sky.job.system.dto;

public record UpsertDepartmentRequest(
        Long parentId,
        String departmentName,
        Integer sortOrder,
        Boolean enabled
) {
}
