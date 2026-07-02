package com.sky.job.system;

import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.system.dto.ReplaceRolePermissionsRequest;
import com.sky.job.system.dto.RoleResponse;
import com.sky.job.system.dto.UpsertRoleRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/system/roles")
public class SystemRoleController {
    private final SystemRoleService roleService;

    public SystemRoleController(SystemRoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public PageResponse<RoleResponse> list(
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return roleService.list(roleName, page, size);
    }

    @GetMapping("/{roleId}")
    public RoleResponse get(@PathVariable Long roleId) {
        return roleService.get(roleId);
    }

    @PostMapping
    public RoleResponse create(@RequestBody UpsertRoleRequest request, HttpServletRequest httpRequest) {
        return roleService.create(request, httpRequest);
    }

    @PutMapping("/{roleId}")
    public RoleResponse update(@PathVariable Long roleId, @RequestBody UpsertRoleRequest request, HttpServletRequest httpRequest) {
        return roleService.update(roleId, request, httpRequest);
    }

    @PutMapping("/{roleId}/permissions")
    public RoleResponse replacePermissions(@PathVariable Long roleId, @RequestBody ReplaceRolePermissionsRequest request, HttpServletRequest httpRequest) {
        return roleService.replacePermissions(roleId, request, httpRequest);
    }

    @DeleteMapping("/{roleId}")
    public Map<String, Boolean> delete(@PathVariable Long roleId, HttpServletRequest httpRequest) {
        roleService.delete(roleId, httpRequest);
        return Map.of("success", true);
    }
}
