package com.sky.job.plugin.management.dto;

public record PluginAccountExportFile(
        String filename,
        String contentType,
        byte[] content
) {
}
