package com.sky.job.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 20) String username,
        @NotBlank @Size(min = 6) String password,
        @NotBlank @Email @Size(max = 100) String email,
        String phone
) {
}
