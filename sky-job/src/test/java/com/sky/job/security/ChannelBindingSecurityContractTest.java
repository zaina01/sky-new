package com.sky.job.security;

import com.sky.job.plugin.runtime.PluginRuntimeController;
import com.sky.job.plugin.runtime.PluginRuntimeService;
import com.sky.job.scheduler.JobController;
import com.sky.job.scheduler.JobService;
import com.sky.job.web.ApiResponseAdvice;
import com.sky.job.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PluginRuntimeController.class, JobController.class})
@Import({
        SecurityConfig.class,
        JwtRequestFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        ApiResponseAdvice.class,
        GlobalExceptionHandler.class
})
class ChannelBindingSecurityContractTest {
    private static final String INSTALLATION_PATH = "/plugins/installations/installation-a/notification-channels";
    private static final String JOB_PATH = "/jobs/42/notification-channels";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PluginRuntimeService runtimeService;
    @MockitoBean
    private JobService jobService;
    @MockitoBean
    private JwtTokenService jwtTokenService;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void installationPoolReadAndWriteRequireAllThreeAuthorities() throws Exception {
        String[] required = {
                PermissionNames.PLUGIN_RUNTIME_MANAGE,
                PermissionNames.PLUGIN_NOTIFICATION_CHANNEL_BIND,
                PermissionNames.NOTIFICATION_CHANNEL_READ
        };

        mockMvc.perform(get(INSTALLATION_PATH).with(authorities(required)))
                .andExpect(status().isOk());
        mockMvc.perform(put(INSTALLATION_PATH)
                        .with(authorities(required))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channelIds\":[]}"))
                .andExpect(status().isOk());

        for (String omitted : required) {
            String[] granted = Arrays.stream(required).filter(permission -> !permission.equals(omitted)).toArray(String[]::new);
            mockMvc.perform(get(INSTALLATION_PATH).with(authorities(granted)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403001));
            mockMvc.perform(put(INSTALLATION_PATH)
                            .with(authorities(granted))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"channelIds\":[]}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403001));
        }
    }

    @Test
    void clearingJobChannelsRequiresJobWriteBindAndChannelRead() throws Exception {
        String[] required = {
                PermissionNames.JOB_WRITE,
                PermissionNames.JOB_NOTIFICATION_BIND,
                PermissionNames.NOTIFICATION_CHANNEL_READ
        };

        mockMvc.perform(put(JOB_PATH)
                        .with(authorities(required))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channelIds\":[]}"))
                .andExpect(status().isOk());

        for (String omitted : required) {
            String[] granted = Arrays.stream(required).filter(permission -> !permission.equals(omitted)).toArray(String[]::new);
            mockMvc.perform(put(JOB_PATH)
                            .with(authorities(granted))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"channelIds\":[]}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403001));
        }
    }

    @Test
    void unknownRouteIsUnauthorizedBeforeLoginAndUnifiedNotFoundAfterLogin() throws Exception {
        mockMvc.perform(get("/route-that-does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401001));

        mockMvc.perform(get("/route-that-does-not-exist").with(authorities()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404001));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor authorities(String... authorities) {
        return user("contract-user").authorities(Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toArray(SimpleGrantedAuthority[]::new));
    }
}
