package com.sky.job.system.dto;

import java.util.Set;

public record ReplaceRolePermissionsRequest(
        Set<String> permissions
) {
}
