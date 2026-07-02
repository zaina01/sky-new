package com.sky.job.system.dto;

public record UpdateSystemUserRequest(
        String email,
        String phone,
        String avatar,
        Boolean enabled
) {
}
