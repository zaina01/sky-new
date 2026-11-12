package com.sky.job.system;

import com.sky.job.security.CustomUserDetailsService;
import com.sky.job.security.JwtTokenService;
import com.sky.job.system.dto.OverviewWindowResponse;
import com.sky.job.system.dto.RecentFailuresResponse;
import com.sky.job.system.dto.SystemOverviewResponse;
import com.sky.job.system.dto.TrendOverviewResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SystemOverviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class SystemOverviewControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemOverviewService overviewService;
    @MockitoBean
    private JwtTokenService jwtTokenService;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void delegatesWithoutParameters() throws Exception {
        when(overviewService.overview(null, null, null, null)).thenReturn(response());

        mockMvc.perform(get("/system/overview").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadedPlugins").value(12))
                .andExpect(jsonPath("$.data.enabledJobs").value(8))
                .andExpect(jsonPath("$.data.window.windowHours").value(24))
                .andExpect(jsonPath("$.data.pluginRuntimeStates").isArray())
                .andExpect(jsonPath("$.data.trends.jobExecutions").isArray())
                .andExpect(jsonPath("$.data.recentFailures.jobExecutions").isArray());

        verify(overviewService).overview(null, null, null, null);
    }

    @Test
    void delegatesWithParameters() throws Exception {
        when(overviewService.overview(null, 12, 14, 10)).thenReturn(response());

        mockMvc.perform(get("/system/overview")
                        .queryParam("windowHours", "12")
                        .queryParam("trendDays", "14")
                        .queryParam("recentLimit", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingRetryNotifications").value(2));

        verify(overviewService).overview(null, 12, 14, 10);
    }

    private SystemOverviewResponse response() {
        return new SystemOverviewResponse(
                12,
                8,
                4,
                2,
                new OverviewWindowResponse(24, LocalDateTime.of(2026, 7, 1, 10, 0), LocalDateTime.of(2026, 7, 2, 10, 0), 7, 5),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new TrendOverviewResponse(List.of(), List.of()),
                new RecentFailuresResponse(List.of(), List.of())
        );
    }
}
