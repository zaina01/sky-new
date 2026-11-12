package com.sky.job.plugin.management.dto;

import java.util.List;

public record PluginAccountViewPreferenceResponse(
        String installationId,
        List<String> fields,
        List<PluginAccountListFieldResponse> availableFields,
        int maxFields
) {
}
