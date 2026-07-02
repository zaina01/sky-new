package com.sky.job.system;

import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.system.dto.PermissionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system/permissions")
public class SystemPermissionController {
    private final SystemPermissionService permissionService;

    public SystemPermissionController(SystemPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    public PageResponse<PermissionResponse> list(
            @RequestParam(required = false) String permissionName,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return permissionService.list(permissionName, page, size);
    }
}
