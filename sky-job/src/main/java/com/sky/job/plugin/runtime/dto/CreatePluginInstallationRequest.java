package com.sky.job.plugin.runtime.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePluginInstallationRequest(
        @NotBlank String pluginId,
        @NotBlank String installationName
) {
}
