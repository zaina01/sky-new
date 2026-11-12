package com.sky.job.system.dto;

import com.sky.job.system.Department;

import java.time.LocalDateTime;
import java.util.List;

public record DepartmentResponse(
        Long departmentId,
        Long parentId,
        String departmentName,
        int sortOrder,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<DepartmentResponse> children
) {
    public static DepartmentResponse from(Department department, List<DepartmentResponse> children) {
        return new DepartmentResponse(department.departmentId(), department.parentId(), department.departmentName(), department.sortOrder(), department.enabled(), department.createdAt(), department.updatedAt(), List.copyOf(children));
    }
}
