package com.sky.job.system;

import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.system.dto.PermissionResponse;
import org.springframework.stereotype.Service;

@Service
public class SystemPermissionService {
    private final PermissionStore permissionStore;

    public SystemPermissionService(PermissionStore permissionStore) {
        this.permissionStore = permissionStore;
    }

    public PageResponse<PermissionResponse> list(String permissionName, Integer page, Integer size) {
        return new PageResponse<>(
                permissionStore.list(permissionName, page, size).stream().map(PermissionResponse::from).toList(),
                normalizedPage(page),
                normalizedSize(size),
                permissionStore.count(permissionName)
        );
    }

    private int normalizedPage(Integer page) {
        return Math.max(page == null ? 1 : page, 1);
    }

    private int normalizedSize(Integer size) {
        return Math.min(Math.max(size == null ? 20 : size, 1), 100);
    }
}
