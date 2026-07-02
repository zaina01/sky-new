package com.sky.job.user;

import java.util.Set;

public record LoginResponse(
        String token,
        UserResponse user,
        long expiresIn,
        Set<String> permissions
) {
}
