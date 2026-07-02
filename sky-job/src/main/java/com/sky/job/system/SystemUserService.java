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
    private final PasswordEncoder passwordEncoder;
    private final SystemLogStore systemLogStore;

    public SystemUserService(UserStore userStore, RoleStore roleStore, PasswordEncoder passwordEncoder, SystemLogStore systemLogStore) {
        this.userStore = userStore;
        this.roleStore = roleStore;
        this.passwordEncoder = passwordEncoder;
        this.systemLogStore = systemLogStore;
    }

    public PageResponse<SystemUserResponse> list(String username, Boolean enabled, Integer page, Integer size) {
        return new PageResponse<>(
                userStore.list(username, enabled, page, size).stream().map(this::response).toList(),
                normalizedPage(page),
                normalizedSize(size),
                userStore.count(username, enabled)
        );
    }

    public SystemUserResponse get(String userId) {
        return response(requireUser(userId));
    }

    @Transactional
    public SystemUserResponse create(CreateSystemUserRequest request, HttpServletRequest httpRequest) {
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
        User user = new User(UUID.randomUUID().toString(), username, passwordEncoder.encode(password), email, blankToNull(request == null ? null : request.phone()), blankToNull(request == null ? null : request.avatar()), request == null || request.enabled() == null || request.enabled(), null, null);
        userStore.insert(user);
        userStore.replaceRoles(user.userId(), roles);
        User stored = requireUser(user.userId());
        systemLogStore.record("SYSTEM_USER", "CREATE_USER", "INFO", "SUCCESS", "USER", stored.userId(), "User created", httpRequest);
        return response(stored);
    }

    @Transactional
    public SystemUserResponse update(String userId, UpdateSystemUserRequest request, HttpServletRequest httpRequest) {
        User user = requireUser(userId);
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
        if (userStore.updateProfile(userId, email, blankToNull(request == null ? null : request.phone()), blankToNull(request == null ? null : request.avatar()), enabled) == 0) {
            throw new IllegalArgumentException("User not found");
        }
        systemLogStore.record("SYSTEM_USER", "UPDATE_USER", "INFO", "SUCCESS", "USER", userId, "User updated", httpRequest);
        return get(userId);
    }

    @Transactional
    public SystemUserResponse updateEnabled(String userId, UpdateUserEnabledRequest request, HttpServletRequest httpRequest) {
        User user = requireUser(userId);
        boolean enabled = request != null && request.enabled();
        if (!enabled) {
            ensureNotLastAdmin(user);
        }
        if (userStore.updateEnabled(userId, enabled) == 0) {
            throw new IllegalArgumentException("User not found");
        }
        systemLogStore.record("SYSTEM_USER", enabled ? "ENABLE_USER" : "DISABLE_USER", "INFO", "SUCCESS", "USER", userId, "User enabled state updated", httpRequest);
        return get(userId);
    }

    public void resetPassword(String userId, ResetUserPasswordRequest request, HttpServletRequest httpRequest) {
        requireUser(userId);
        String password = required(request == null ? null : request.password(), "Invalid password");
        userStore.updatePassword(userId, passwordEncoder.encode(password));
        systemLogStore.record("SYSTEM_USER", "RESET_PASSWORD", "INFO", "SUCCESS", "USER", userId, "User password reset", httpRequest);
    }

    @Transactional
    public SystemUserResponse replaceRoles(String userId, ReplaceUserRolesRequest request, HttpServletRequest httpRequest) {
        User user = requireUser(userId);
        Set<String> roles = normalizedRoles(request == null ? null : request.roles());
        requireRoles(roles);
        if (!roles.contains(ADMIN_ROLE)) {
            ensureNotLastAdmin(user);
        }
        userStore.replaceRoles(userId, roles);
        systemLogStore.record("SYSTEM_USER", "ASSIGN_ROLES", "INFO", "SUCCESS", "USER", userId, "User roles replaced", httpRequest);
        return get(userId);
    }

    @Transactional
    public void delete(String userId, HttpServletRequest httpRequest) {
        User user = requireUser(userId);
        ensureNotLastAdmin(user);
        if (userStore.delete(userId) == 0) {
            throw new IllegalArgumentException("User not found");
        }
        systemLogStore.record("SYSTEM_USER", "DELETE_USER", "INFO", "SUCCESS", "USER", userId, "User deleted", httpRequest);
    }

    private SystemUserResponse response(User user) {
        return SystemUserResponse.from(user, userStore.roles(user.userId()), userStore.permissions(user.userId()));
    }

    private User requireUser(String userId) {
        return userStore.findByUserId(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
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
