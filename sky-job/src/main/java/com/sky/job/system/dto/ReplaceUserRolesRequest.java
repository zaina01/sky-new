package com.sky.job.system.dto;

import java.util.Set;

public record ReplaceUserRolesRequest(
        Set<String> roles
) {
}
