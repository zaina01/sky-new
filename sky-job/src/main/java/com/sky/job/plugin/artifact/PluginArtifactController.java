package com.sky.job.plugin.artifact;

import com.sky.job.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/plugins/artifacts")
public class PluginArtifactController {
    private final PluginArtifactService service;

    public PluginArtifactController(PluginArtifactService service) {
        this.service = service;
    }

    @PostMapping
    public PluginUploadResult upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return service.upload(file, user.username());
    }
}
