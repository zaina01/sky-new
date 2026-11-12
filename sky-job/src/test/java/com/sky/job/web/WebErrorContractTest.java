package com.sky.job.web;

import com.sky.job.plugin.management.PluginManagementController;
import com.sky.job.plugin.management.PluginManagementService;
import com.sky.job.plugin.management.dto.PluginAccountExportFile;
import com.sky.job.security.CustomUserDetailsService;
import com.sky.job.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {WebErrorContractTest.ContractController.class, PluginManagementController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(WebErrorContractTest.ContractController.class)
class WebErrorContractTest {
    private static final String INSTALLATION_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenService jwtTokenService;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;
    @MockitoBean
    private PluginManagementService managementService;

    @Test
    void unmatchedRouteUsesUnifiedNotFoundEnvelope() throws Exception {
        mockMvc.perform(get("/route-that-does-not-exist").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(404001))
                .andExpect(jsonPath("$.msg").value("Resource not found"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void businessNotFoundPreservesBackendMessage() throws Exception {
        mockMvc.perform(get("/contract/not-found").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404001))
                .andExpect(jsonPath("$.msg").value("Plugin installation not found"));
    }

    @Test
    void exportFailureIsJsonThatBlobClientsCanDecode() throws Exception {
        when(managementService.exportAccounts(
                eq(INSTALLATION_ID), isNull(), isNull(), isNull(), isNull(), isNull(), eq("invalid"), isNull(), isNull()
        )).thenThrow(new IllegalArgumentException("Export format is invalid"));

        byte[] response = mockMvc.perform(get("/plugins/installations/{installationId}/accounts/export", INSTALLATION_ID)
                        .queryParam("format", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400001))
                .andExpect(jsonPath("$.msg").value("Export format is invalid"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(new String(response, StandardCharsets.UTF_8))
                .contains("\"code\":400001")
                .contains("\"data\":null");
    }

    @Test
    void successfulExportRemainsBinaryAndIsNotApiWrapped() throws Exception {
        byte[] exported = new byte[]{1, 2, 3};
        when(managementService.exportAccounts(
                eq(INSTALLATION_ID), isNull(), isNull(), isNull(), isNull(), isNull(), eq("csv"), isNull(), isNull()
        )).thenReturn(new PluginAccountExportFile("accounts.csv", "application/octet-stream", exported));

        mockMvc.perform(get("/plugins/installations/{installationId}/accounts/export", INSTALLATION_ID)
                        .queryParam("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(content().bytes(exported));
    }

    @RestController
    @RequestMapping("/contract")
    public static class ContractController {
        @GetMapping("/not-found")
        public Object notFound() {
            throw new ResourceNotFoundException("Plugin installation not found");
        }

    }
}
