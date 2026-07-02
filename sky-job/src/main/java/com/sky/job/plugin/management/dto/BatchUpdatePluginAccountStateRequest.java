package com.sky.job.plugin.management.dto;

import java.util.List;

public record BatchUpdatePluginAccountStateRequest(
        List<String> accountIds,
        boolean enabled
) {
}
