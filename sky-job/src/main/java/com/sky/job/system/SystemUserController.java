package com.sky.job.system;

import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.system.dto.CreateSystemUserRequest;
import com.sky.job.system.dto.ReplaceUserRolesRequest;
import com.sky.job.system.dto.ResetUserPasswordRequest;
import com.sky.job.system.dto.SystemUserResponse;
import com.sky.job.system.dto.UpdateSystemUserRequest;
import com.sky.job.system.dto.UpdateUserEnabledRequest;
import jakarta.servlet.http.HttpServletRequest;
import com.sky.job.security.AuthenticatedUser;
import com.sky.job.security.PermissionNames;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/system/users")
public class SystemUserController {
    private final SystemUserService userService;

    public SystemUserController(SystemUserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public PageResponse<SystemUserResponse> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Long departmentId,
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return userService.list(username, enabled, departmentId, actor, page, size);
    }

    @GetMapping("/{userId}")
    public SystemUserResponse get(@PathVariable String userId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return userService.get(userId, actor, PermissionNames.SYSTEM_USER_READ);
    }

    @PostMapping
    public SystemUserResponse create(@RequestBody CreateSystemUserRequest request, @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest httpRequest) {
        return userService.create(request, actor, httpRequest);
    }

    @PutMapping("/{userId}")
    public SystemUserResponse update(@PathVariable String userId, @RequestBody UpdateSystemUserRequest request, @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest httpRequest) {
        return userService.update(userId, request, actor, httpRequest);
    }

    @PatchMapping("/{userId}/enabled")
    public SystemUserResponse updateEnabled(@PathVariable String userId, @RequestBody UpdateUserEnabledRequest request, @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest httpRequest) {
        return userService.updateEnabled(userId, request, actor, httpRequest);
    }

    @PostMapping("/{userId}/password/reset")
    public Map<String, Boolean> resetPassword(@PathVariable String userId, @RequestBody ResetUserPasswordRequest request, @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest httpRequest) {
        userService.resetPassword(userId, request, actor, httpRequest);
        return Map.of("success", true);
    }

    @PutMapping("/{userId}/roles")
    public SystemUserResponse replaceRoles(@PathVariable String userId, @RequestBody ReplaceUserRolesRequest request, @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest httpRequest) {
        return userService.replaceRoles(userId, request, actor, httpRequest);
    }

    @DeleteMapping("/{userId}")
    public Map<String, Boolean> delete(@PathVariable String userId, @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest httpRequest) {
        userService.delete(userId, actor, httpRequest);
        return Map.of("success", true);
    }
}
