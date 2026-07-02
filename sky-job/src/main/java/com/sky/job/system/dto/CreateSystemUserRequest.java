package com.sky.job.system.dto;

import java.util.Set;

public record CreateSystemUserRequest(
        String username,
        String password,
        String email,
        String phone,
        String avatar,
        Boolean enabled,
        Set<String> roles
) {
}
