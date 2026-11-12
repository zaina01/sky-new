package com.sky.job.plugin.management.dto;

import java.util.List;

public record BatchDeletePluginAccountRequest(
        List<String> accountIds
) {
}
