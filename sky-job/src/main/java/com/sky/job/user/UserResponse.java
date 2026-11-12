package com.sky.job.user;

import java.time.LocalDateTime;
import java.util.Set;

public record UserResponse(
        String userId,
        String username,
        String email,
        String phone,
        String avatar,
        Long departmentId,
        String departmentName,
        LocalDateTime registerTime,
        Set<String> roles,
        Set<String> permissions
) {
    public static UserResponse from(User user, Set<String> roles, Set<String> permissions) {
        return new UserResponse(
                user.userId(),
                user.username(),
                user.email(),
                user.phone(),
                user.avatar(),
                user.departmentId(),
                user.departmentName(),
                user.createdAt(),
                roles,
                permissions
        );
    }
}
