package com.sky.job.system;

import com.sky.job.audit.SystemLogStore;
import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.system.dto.CreateSystemUserRequest;
import com.sky.job.system.dto.ReplaceUserRolesRequest;
import com.sky.job.system.dto.ResetUserPasswordRequest;
import com.sky.job.system.dto.SystemUserResponse;
import com.sky.job.system.dto.UpdateSystemUserRequest;
import com.sky.job.system.dto.UpdateUserEnabledRequest;
import com.sky.job.user.User;
import com.sky.job.user.UserStore;
import com.sky.job.security.AuthenticatedUser;
import com.sky.job.security.DataScopeService;
import com.sky.job.security.EffectiveDataScope;
import com.sky.job.security.PermissionNames;
import com.sky.job.web.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SystemUserService {
    private static final String ADMIN_ROLE = "ADMIN";

    private final UserStore userStore;
    private final RoleStore roleStore;
    private final DepartmentStore departmentStore;
    private final DataScopeService dataScopeService;
    private final PasswordEncoder passwordEncoder;
    private final SystemLogStore systemLogStore;

    public SystemUserService(UserStore userStore, RoleStore roleStore, DepartmentStore departmentStore, DataScopeService dataScopeService, PasswordEncoder passwordEncoder, SystemLogStore systemLogStore) {
        this.userStore = userStore;
        this.roleStore = roleStore;
        this.departmentStore = departmentStore;
        this.dataScopeService = dataScopeService;
        this.passwordEncoder = passwordEncoder;
        this.systemLogStore = systemLogStore;
    }

    public PageResponse<SystemUserResponse> list(String username, Boolean enabled, Long departmentId, AuthenticatedUser actor, Integer page, Integer size) {
        EffectiveDataScope scope = dataScopeService.resolve(actor, PermissionNames.SYSTEM_USER_READ);
        java.util.List<Long> departmentIds = departmentId == null ? null : departmentStore.descendantIds(departmentId);
        return new PageResponse<>(
                userStore.list(username, enabled, departmentIds, scope, page, size).stream().map(this::response).toList(),
                normalizedPage(page),
                normalizedSize(size),
                userStore.count(username, enabled, departmentIds, scope)
        );
    }

    public SystemUserResponse get(String userId, AuthenticatedUser actor, String permissionName) {
        return response(requireScopedUser(userId, actor, permissionName));
    }

    @Transactional
    public SystemUserResponse create(CreateSystemUserRequest request, AuthenticatedUser actor, HttpServletRequest httpRequest) {
        String username = required(request == null ? null : request.username(), "Invalid username");
        String password = required(request == null ? null : request.password(), "Invalid password");
        String email = required(request == null ? null : request.email(), "Invalid email");
        if (userStore.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userStore.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }
        if (userStore.existsByPhone(request == null ? null : request.phone())) {
            throw new IllegalArgumentException("Phone already exists");
        }
        Set<String> roles = normalizedRoles(request == null ? null : request.roles());
        requireRoles(roles);
        Long departmentId = requireAssignableDepartment(request == null ? null : request.departmentId(), actor, PermissionNames.SYSTEM_USER_WRITE);
        User user = new User(UUID.randomUUID().toString(), username, passwordEncoder.encode(password), email, blankToNull(request == null ? null : request.phone()), blankToNull(request == null ? null : request.avatar()), departmentId, null, true, request == null || request.enabled() == null || request.enabled(), null, null);
        userStore.insert(user);
        userStore.replaceRoles(user.userId(), roles);
        User stored = requireUser(user.userId());
        systemLogStore.record("SYSTEM_USER", "CREATE_USER", "INFO", "SUCCESS", "USER", stored.userId(), "User created", httpRequest);
        return response(stored);
    }

    @Transactional
    public SystemUserResponse update(String userId, UpdateSystemUserRequest request, AuthenticatedUser actor, HttpServletRequest httpRequest) {
        User user = requireScopedUser(userId, actor, PermissionNames.SYSTEM_USER_WRITE);
        String email = required(request == null ? null : request.email(), "Invalid email");
        if (userStore.existsByEmailForOtherUser(email, userId)) {
            throw new IllegalArgumentException("Email already exists");
        }
        if (userStore.existsByPhoneForOtherUser(request == null ? null : request.phone(), userId)) {
            throw new IllegalArgumentException("Phone already exists");
        }
        boolean enabled = request == null || request.enabled() == null ? user.enabled() : request.enabled();
        if (!enabled) {
            ensureNotLastAdmin(user);
        }
        Long departmentId = request == null || request.departmentId() == null ? user.departmentId() : requireAssignableDepartment(request.departmentId(), actor, PermissionNames.SYSTEM_USER_WRITE);
        if (userStore.updateProfile(userId, email, blankToNull(request == null ? null : request.phone()), blankToNull(request == null ? null : request.avatar()), departmentId, enabled) == 0) {
            throw new ResourceNotFoundException("User not found");
        }
        systemLogStore.record("SYSTEM_USER", "UPDATE_USER", "INFO", "SUCCESS", "USER", userId, "User updated", httpRequest);
        return get(userId, actor, PermissionNames.SYSTEM_USER_WRITE);
    }

    @Transactional
    public SystemUserResponse updateEnabled(String userId, UpdateUserEnabledRequest request, AuthenticatedUser actor, HttpServletRequest httpRequest) {
        User user = requireScopedUser(userId, actor, PermissionNames.SYSTEM_USER_WRITE);
        boolean enabled = request != null && request.enabled();
        if (!enabled) {
            ensureNotLastAdmin(user);
        }
        if (userStore.updateEnabled(userId, enabled) == 0) {
            throw new ResourceNotFoundException("User not found");
        }
        systemLogStore.record("SYSTEM_USER", enabled ? "ENABLE_USER" : "DISABLE_USER", "INFO", "SUCCESS", "USER", userId, "User enabled state updated", httpRequest);
        return get(userId, actor, PermissionNames.SYSTEM_USER_WRITE);
    }

    public void resetPassword(String userId, ResetUserPasswordRequest request, AuthenticatedUser actor, HttpServletRequest httpRequest) {
        requireScopedUser(userId, actor, PermissionNames.SYSTEM_USER_PASSWORD_RESET);
        String password = required(request == null ? null : request.password(), "Invalid password");
        userStore.updatePassword(userId, passwordEncoder.encode(password));
        systemLogStore.record("SYSTEM_USER", "RESET_PASSWORD", "INFO", "SUCCESS", "USER", userId, "User password reset", httpRequest);
    }

    @Transactional
    public SystemUserResponse replaceRoles(String userId, ReplaceUserRolesRequest request, AuthenticatedUser actor, HttpServletRequest httpRequest) {
        User user = requireScopedUser(userId, actor, PermissionNames.SYSTEM_USER_WRITE);
        Set<String> roles = normalizedRoles(request == null ? null : request.roles());
        requireRoles(roles);
        if (!roles.contains(ADMIN_ROLE)) {
            ensureNotLastAdmin(user);
        }
        userStore.replaceRoles(userId, roles);
        systemLogStore.record("SYSTEM_USER", "ASSIGN_ROLES", "INFO", "SUCCESS", "USER", userId, "User roles replaced", httpRequest);
        return get(userId, actor, PermissionNames.SYSTEM_USER_WRITE);
    }

    @Transactional
    public void delete(String userId, AuthenticatedUser actor, HttpServletRequest httpRequest) {
        User user = requireScopedUser(userId, actor, PermissionNames.SYSTEM_USER_DELETE);
        ensureNotLastAdmin(user);
        if (userStore.hasOwnedResources(userId)) throw new IllegalStateException("User owns resources and must be disabled or transferred before deletion");
        if (userStore.delete(userId) == 0) {
            throw new ResourceNotFoundException("User not found");
        }
        systemLogStore.record("SYSTEM_USER", "DELETE_USER", "INFO", "SUCCESS", "USER", userId, "User deleted", httpRequest);
    }

    private SystemUserResponse response(User user) {
        return SystemUserResponse.from(user, userStore.roles(user.userId()), userStore.permissions(user.userId()));
    }

    private User requireUser(String userId) {
        return userStore.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private User requireScopedUser(String userId, AuthenticatedUser actor, String permissionName) {
        User user = requireUser(userId);
        if (!dataScopeService.allows(dataScopeService.resolve(actor, permissionName), user.userId(), user.departmentId())) {
            throw new ResourceNotFoundException("User not found");
        }
        return user;
    }

    private Long requireAssignableDepartment(Long departmentId, AuthenticatedUser actor, String permissionName) {
        if (departmentId == null) throw new IllegalArgumentException("Department is required");
        Department department = departmentStore.findById(departmentId).orElseThrow(() -> new ResourceNotFoundException("Department not found"));
        if (!department.enabled()) throw new IllegalStateException("Department is disabled");
        EffectiveDataScope scope = dataScopeService.resolve(actor, permissionName);
        if (!scope.all() && !scope.departmentIds().contains(departmentId)) throw new org.springframework.security.access.AccessDeniedException("Department is outside data scope");
        return departmentId;
    }

    private void requireRoles(Set<String> roles) {
        for (String role : roles) {
            if (roleStore.findByName(role).isEmpty()) {
                throw new IllegalArgumentException("Role not found");
            }
        }
    }

    private void ensureNotLastAdmin(User user) {
        if (user.enabled() && userStore.hasRole(user.userId(), ADMIN_ROLE) && userStore.countEnabledAdmins() <= 1) {
            throw new IllegalStateException("Cannot remove last enabled admin");
        }
    }

    private Set<String> normalizedRoles(Set<String> roles) {
        if (roles == null) {
            return Set.of();
        }
        return roles.stream().map(role -> required(role, "Invalid role name")).collect(Collectors.toUnmodifiableSet());
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int normalizedPage(Integer page) {
        return Math.max(page == null ? 1 : page, 1);
    }

    private int normalizedSize(Integer size) {
        return Math.min(Math.max(size == null ? 20 : size, 1), 100);
    }
}
