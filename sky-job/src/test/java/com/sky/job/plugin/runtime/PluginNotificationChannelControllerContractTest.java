package com.sky.job.plugin.runtime;

import com.sky.job.plugin.runtime.dto.ReplaceNotificationChannelsRequest;
import com.sky.job.security.CustomUserDetailsService;
import com.sky.job.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PluginRuntimeController.class)
@AutoConfigureMockMvc(addFilters = false)
class PluginNotificationChannelControllerContractTest {
    private static final String INSTALLATION_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PluginRuntimeService runtimeService;
    @MockitoBean
    private JwtTokenService jwtTokenService;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void readsKnownInstallationChannelPoolWithoutUsingAnEmptyFallback() throws Exception {
        when(runtimeService.notificationChannelIds(INSTALLATION_ID, null)).thenReturn(List.of("channel-a", "channel-b"));

        mockMvc.perform(get("/plugins/installations/{installationId}/notification-channels", INSTALLATION_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0]").value("channel-a"))
                .andExpect(jsonPath("$.data[1]").value("channel-b"));

        verify(runtimeService).notificationChannelIds(INSTALLATION_ID, null);
    }

    @Test
    void replacesPoolUsingChannelIdsRequestField() throws Exception {
        when(runtimeService.replaceNotificationChannels(
                org.mockito.ArgumentMatchers.eq(INSTALLATION_ID),
                org.mockito.ArgumentMatchers.any(ReplaceNotificationChannelsRequest.class),
                isNull()
        )).thenReturn(List.of("channel-b"));

        mockMvc.perform(put("/plugins/installations/{installationId}/notification-channels", INSTALLATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channelIds\":[\"channel-b\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0]").value("channel-b"));
    }
}
