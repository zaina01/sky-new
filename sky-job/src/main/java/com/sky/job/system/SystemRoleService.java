package com.sky.job.system;

import com.sky.job.audit.SystemLogStore;
import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.security.PermissionNames;
import com.sky.job.system.dto.ReplaceRolePermissionsRequest;
import com.sky.job.system.dto.RoleResponse;
import com.sky.job.system.dto.UpsertRoleRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SystemRoleService {
    private static final String ADMIN_ROLE = "ADMIN";
    private static final Set<String> ADMIN_REQUIRED_PERMISSIONS = Set.of(
            PermissionNames.SYSTEM_USER_READ,
            PermissionNames.SYSTEM_USER_WRITE,
            PermissionNames.SYSTEM_USER_DELETE,
            PermissionNames.SYSTEM_USER_PASSWORD_RESET,
            PermissionNames.SYSTEM_ROLE_READ,
            PermissionNames.SYSTEM_ROLE_WRITE,
            PermissionNames.SYSTEM_ROLE_DELETE,
            PermissionNames.SYSTEM_PERMISSION_READ,
            PermissionNames.SYSTEM_OVERVIEW_READ,
            PermissionNames.SYSTEM_SETTING_READ,
            PermissionNames.SYSTEM_SETTING_WRITE
    );

    private final RoleStore roleStore;
    private final PermissionStore permissionStore;
    private final SystemLogStore systemLogStore;

    public SystemRoleService(RoleStore roleStore, PermissionStore permissionStore, SystemLogStore systemLogStore) {
        this.roleStore = roleStore;
        this.permissionStore = permissionStore;
        this.systemLogStore = systemLogStore;
    }

    public PageResponse<RoleResponse> list(String roleName, Integer page, Integer size) {
        return new PageResponse<>(
                roleStore.list(roleName, page, size).stream().map(this::response).toList(),
                normalizedPage(page),
                normalizedSize(size),
                roleStore.count(roleName)
        );
    }

    public RoleResponse get(Long roleId) {
        return response(requireRole(roleId));
    }

    @Transactional
    public RoleResponse create(UpsertRoleRequest request, HttpServletRequest httpRequest) {
        String roleName = required(request == null ? null : request.roleName(), "Invalid role name");
        if (roleStore.existsByName(roleName)) {
            throw new IllegalArgumentException("Role already exists");
        }
        long roleId = roleStore.insert(roleName, trimToEmpty(request == null ? null : request.description()));
        systemLogStore.record("SYSTEM_ROLE", "CREATE_ROLE", "INFO", "SUCCESS", "ROLE", String.valueOf(roleId), "Role created", httpRequest);
        return get(roleId);
    }

    @Transactional
    public RoleResponse update(Long roleId, UpsertRoleRequest request, HttpServletRequest httpRequest) {
        Role role = requireRole(roleId);
        String roleName = required(request == null ? null : request.roleName(), "Invalid role name");
        if (ADMIN_ROLE.equals(role.roleName()) && !ADMIN_ROLE.equals(roleName)) {
            throw new IllegalStateException("Cannot rename ADMIN role");
        }
        if (roleStore.existsByNameForOtherRole(roleName, roleId)) {
            throw new IllegalArgumentException("Role already exists");
        }
        if (roleStore.update(roleId, roleName, trimToEmpty(request == null ? null : request.description())) == 0) {
            throw new IllegalArgumentException("Role not found");
        }
        systemLogStore.record("SYSTEM_ROLE", "UPDATE_ROLE", "INFO", "SUCCESS", "ROLE", String.valueOf(roleId), "Role updated", httpRequest);
        return get(roleId);
    }

    @Transactional
    public RoleResponse replacePermissions(Long roleId, ReplaceRolePermissionsRequest request, HttpServletRequest httpRequest) {
        Role role = requireRole(roleId);
        Set<String> permissions = normalizedPermissions(request == null ? null : request.permissions());
        Set<String> existing = permissionStore.existingNames(permissions);
        if (!existing.containsAll(permissions)) {
            throw new IllegalArgumentException("Permission not found");
        }
        if (ADMIN_ROLE.equals(role.roleName()) && !permissions.containsAll(ADMIN_REQUIRED_PERMISSIONS)) {
            throw new IllegalStateException("Cannot remove required ADMIN permissions");
        }
        roleStore.replacePermissions(roleId, permissions);
        systemLogStore.record("SYSTEM_ROLE", "ASSIGN_PERMISSIONS", "INFO", "SUCCESS", "ROLE", String.valueOf(roleId), "Role permissions replaced", httpRequest);
        return get(roleId);
    }

    @Transactional
    public void delete(Long roleId, HttpServletRequest httpRequest) {
        Role role = requireRole(roleId);
        if (ADMIN_ROLE.equals(role.roleName())) {
            throw new IllegalStateException("Cannot delete ADMIN role");
        }
        if (roleStore.isAssigned(roleId)) {
            throw new IllegalStateException("Cannot delete assigned role");
        }
        if (roleStore.delete(roleId) == 0) {
            throw new IllegalArgumentException("Role not found");
        }
        systemLogStore.record("SYSTEM_ROLE", "DELETE_ROLE", "INFO", "SUCCESS", "ROLE", String.valueOf(roleId), "Role deleted", httpRequest);
    }

    private RoleResponse response(Role role) {
        return RoleResponse.from(role, roleStore.permissions(role.roleId()));
    }

    private Role requireRole(Long roleId) {
        return roleStore.findById(roleId).orElseThrow(() -> new IllegalArgumentException("Role not found"));
    }

    private Set<String> normalizedPermissions(Set<String> permissions) {
        if (permissions == null) {
            return Set.of();
        }
        return permissions.stream().map(permission -> required(permission, "Invalid permission name")).collect(Collectors.toUnmodifiableSet());
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private int normalizedPage(Integer page) {
        return Math.max(page == null ? 1 : page, 1);
    }

    private int normalizedSize(Integer size) {
        return Math.min(Math.max(size == null ? 20 : size, 1), 100);
    }
}
