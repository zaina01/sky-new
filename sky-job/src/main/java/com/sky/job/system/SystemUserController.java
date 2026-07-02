package com.sky.job.system;

import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.system.dto.CreateSystemUserRequest;
import com.sky.job.system.dto.ReplaceUserRolesRequest;
import com.sky.job.system.dto.ResetUserPasswordRequest;
import com.sky.job.system.dto.SystemUserResponse;
import com.sky.job.system.dto.UpdateSystemUserRequest;
import com.sky.job.system.dto.UpdateUserEnabledRequest;
import jakarta.servlet.http.HttpServletRequest;
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
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return userService.list(username, enabled, page, size);
    }

    @GetMapping("/{userId}")
    public SystemUserResponse get(@PathVariable String userId) {
        return userService.get(userId);
    }

    @PostMapping
    public SystemUserResponse create(@RequestBody CreateSystemUserRequest request, HttpServletRequest httpRequest) {
        return userService.create(request, httpRequest);
    }

    @PutMapping("/{userId}")
    public SystemUserResponse update(@PathVariable String userId, @RequestBody UpdateSystemUserRequest request, HttpServletRequest httpRequest) {
        return userService.update(userId, request, httpRequest);
    }

    @PatchMapping("/{userId}/enabled")
    public SystemUserResponse updateEnabled(@PathVariable String userId, @RequestBody UpdateUserEnabledRequest request, HttpServletRequest httpRequest) {
        return userService.updateEnabled(userId, request, httpRequest);
    }

    @PostMapping("/{userId}/password/reset")
    public Map<String, Boolean> resetPassword(@PathVariable String userId, @RequestBody ResetUserPasswordRequest request, HttpServletRequest httpRequest) {
        userService.resetPassword(userId, request, httpRequest);
        return Map.of("success", true);
    }

    @PutMapping("/{userId}/roles")
    public SystemUserResponse replaceRoles(@PathVariable String userId, @RequestBody ReplaceUserRolesRequest request, HttpServletRequest httpRequest) {
        return userService.replaceRoles(userId, request, httpRequest);
    }

    @DeleteMapping("/{userId}")
    public Map<String, Boolean> delete(@PathVariable String userId, HttpServletRequest httpRequest) {
        userService.delete(userId, httpRequest);
        return Map.of("success", true);
    }
}
