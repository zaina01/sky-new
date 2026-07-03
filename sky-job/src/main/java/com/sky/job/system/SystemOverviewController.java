package com.sky.job.system;

import com.sky.job.system.dto.SystemOverviewResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system/overview")
public class SystemOverviewController {
    private final SystemOverviewService overviewService;

    public SystemOverviewController(SystemOverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping
    public SystemOverviewResponse overview(
            @RequestParam(required = false) Integer windowHours,
            @RequestParam(required = false) Integer trendDays,
            @RequestParam(required = false) Integer recentLimit
    ) {
        return overviewService.overview(windowHours, trendDays, recentLimit);
    }
}
