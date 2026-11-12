package com.sky.job.plugin.management;

import com.sky.job.plugin.management.dto.BatchDeletePluginAccountRequest;
import com.sky.job.plugin.management.dto.BatchDeletePluginAccountResponse;
import com.sky.job.plugin.management.dto.UpdatePluginAccountGroupRequest;
import com.sky.job.plugin.management.dto.BatchUpdatePluginAccountGroupResponse;
import com.sky.job.plugin.management.dto.BatchPluginAccountGroupReceipt;
import com.sky.job.plugin.management.dto.BatchPluginAccountDeleteReceipt;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PluginManagementController.class)
@AutoConfigureMockMvc(addFilters = false)
class PluginManagementControllerContractTest {
    private static final String INSTALLATION_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PluginManagementService managementService;
    @MockitoBean
    private JwtTokenService jwtTokenService;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void deletesAccountsUsingCollectionDeleteRequestBody() throws Exception {
        when(managementService.deleteAccounts(eq(INSTALLATION_ID), any(BatchDeletePluginAccountRequest.class), isNull()))
                .thenReturn(new BatchDeletePluginAccountResponse(
                        INSTALLATION_ID,
                        2,
                        1,
                        1,
                        List.of(
                                new BatchPluginAccountDeleteReceipt("account-a", true, "Deleted"),
                                new BatchPluginAccountDeleteReceipt("account-b", false, "Plugin account not found")
                        )
                ));

        mockMvc.perform(delete("/plugins/installations/{installationId}/accounts", INSTALLATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountIds\":[\"account-a\",\"account-b\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.installationId").value(INSTALLATION_ID))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.succeeded").value(1))
                .andExpect(jsonPath("$.data.failed").value(1))
                .andExpect(jsonPath("$.data.receipts[0].accountId").value("account-a"))
                .andExpect(jsonPath("$.data.receipts[0].success").value(true))
                .andExpect(jsonPath("$.data.receipts[0].message").value("Deleted"))
                .andExpect(jsonPath("$.data.receipts[1].accountId").value("account-b"))
                .andExpect(jsonPath("$.data.receipts[1].success").value(false))
                .andExpect(jsonPath("$.data.receipts[1].message").value("Plugin account not found"));

        verify(managementService).deleteAccounts(
                eq(INSTALLATION_ID),
                org.mockito.ArgumentMatchers.argThat(request -> request.accountIds().equals(List.of("account-a", "account-b"))),
                isNull()
        );
    }

    @Test
    void rejectsMissingBatchDeleteRequestBody() throws Exception {
        when(managementService.deleteAccounts(eq(INSTALLATION_ID), isNull(), isNull()))
                .thenThrow(new IllegalArgumentException("Account ids are required"));

        mockMvc.perform(delete("/plugins/installations/{installationId}/accounts", INSTALLATION_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400001))
                .andExpect(jsonPath("$.msg").value("Account ids are required"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void patchPreservesExplicitNullAndDistinguishesOmission() throws Exception {
        mockMvc.perform(patch("/plugins/installations/{installationId}/accounts/a/group", INSTALLATION_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"groupId\":null}"))
                .andExpect(status().isOk());
        verify(managementService).updateAccountGroup(eq(INSTALLATION_ID), eq("a"),
                argThat(request -> request.groupIdPresent() && request.groupId() == null), isNull());
        when(managementService.updateAccountGroup(eq(INSTALLATION_ID), eq("a"),
                argThat(request -> !request.groupIdPresent()), isNull()))
                .thenThrow(new IllegalArgumentException("Group id field is required"));
        mockMvc.perform(patch("/plugins/installations/{installationId}/accounts/a/group", INSTALLATION_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400001));
    }

    @Test
    void forwardsGroupFiltersAndBatchMoveBody() throws Exception {
        mockMvc.perform(get("/plugins/installations/{installationId}/accounts", INSTALLATION_ID)
                        .queryParam("groupId", "10").queryParam("ungrouped", "false")
                        .queryParam("page", "2").queryParam("size", "5"))
                .andExpect(status().isOk());
        verify(managementService).listAccounts(INSTALLATION_ID, null, null, null, 10L, false, null, 2, 5);
        when(managementService.updateAccountsGroup(eq(INSTALLATION_ID), any(UpdatePluginAccountGroupRequest.class), isNull()))
                .thenReturn(new BatchUpdatePluginAccountGroupResponse(INSTALLATION_ID, 10L, 1, 1, 0,
                        List.of(new BatchPluginAccountGroupReceipt("a", true, "Updated"))));
        mockMvc.perform(patch("/plugins/installations/{installationId}/accounts/group", INSTALLATION_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accountIds\":[\"a\"],\"groupId\":10}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.groupId").value(10))
                .andExpect(jsonPath("$.data.succeeded").value(1));
        verify(managementService).updateAccountsGroup(eq(INSTALLATION_ID),
                argThat(request -> request.groupIdPresent() && request.groupId().equals(10L) && request.accountIds().equals(List.of("a"))), isNull());
    }
}
