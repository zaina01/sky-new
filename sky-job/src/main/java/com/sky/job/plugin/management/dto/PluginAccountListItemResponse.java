package com.sky.job.plugin.management.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.Map;

public record PluginAccountListItemResponse(
        String installationId,
        String accountId,
        String displayName,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Map<String, JsonNode> displayValues,
        Long groupId,
        String groupName
) {
}
