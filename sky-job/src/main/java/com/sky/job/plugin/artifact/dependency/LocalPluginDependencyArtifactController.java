package com.sky.job.plugin.artifact.dependency;

import com.sky.job.plugin.artifact.dependency.dto.LocalPluginDependencyArtifactResponse;
import com.sky.job.plugin.artifact.dependency.dto.LocalPluginDependencyUploadResponse;
import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/plugins/dependencies/local-artifacts")
public class LocalPluginDependencyArtifactController {
    private final LocalPluginDependencyArtifactService service;

    public LocalPluginDependencyArtifactController(LocalPluginDependencyArtifactService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<LocalPluginDependencyArtifactResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return service.list(keyword, page, size);
    }

    @PostMapping
    public LocalPluginDependencyUploadResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam String groupId,
            @RequestParam String artifactId,
            @RequestParam String version,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.upload(file, groupId, artifactId, version, user.username());
    }
}
