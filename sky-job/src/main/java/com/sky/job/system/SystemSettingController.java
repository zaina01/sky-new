package com.sky.job.system;

import com.sky.job.security.AuthenticatedUser;
import com.sky.job.system.dto.BatchUpdateSystemSettingsRequest;
import com.sky.job.system.dto.SystemSettingResponse;
import com.sky.job.system.dto.UpdateSystemSettingRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/system/settings")
public class SystemSettingController {
    private final SystemSettingService service;

    public SystemSettingController(SystemSettingService service) {
        this.service = service;
    }

    @GetMapping
    public List<SystemSettingResponse> list(@RequestParam(required = false) String category) {
        return service.list(category);
    }

    @GetMapping("/{key}")
    public SystemSettingResponse get(@PathVariable String key) {
        return service.get(key);
    }

    @PutMapping("/{key}")
    public SystemSettingResponse update(
            @PathVariable String key,
            @RequestBody UpdateSystemSettingRequest request,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest
    ) {
        return service.update(key, request, user.username(), httpRequest);
    }

    @PutMapping
    public List<SystemSettingResponse> updateBatch(
            @RequestBody BatchUpdateSystemSettingsRequest request,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest
    ) {
        return service.updateBatch(request, user.username(), httpRequest);
    }
}
