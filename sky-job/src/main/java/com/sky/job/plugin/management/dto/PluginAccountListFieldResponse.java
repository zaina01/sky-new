package com.sky.job.plugin.management.dto;

import java.util.List;

public record PluginAccountListFieldResponse(
        String name,
        String label,
        String type,
        List<String> options
) {
}
