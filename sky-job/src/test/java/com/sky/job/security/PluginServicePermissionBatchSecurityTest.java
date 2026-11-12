package com.sky.job.security;

import com.sky.job.plugin.callback.PluginServicePermissionController;
import com.sky.job.plugin.callback.PluginServicePermissionService;
import com.sky.job.plugin.callback.dto.BatchUpsertPluginServicePermissionRequest;
import com.sky.job.plugin.callback.dto.PluginServicePermissionResponse;
import com.sky.job.web.ApiResponseAdvice;
import com.sky.job.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PluginServicePermissionController.class)
@Import({SecurityConfig.class, JwtRequestFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, ApiResponseAdvice.class, GlobalExceptionHandler.class})
class PluginServicePermissionBatchSecurityTest {
    private static final String PATH = "/plugin-service/permissions/batch";
    private static final String BODY = """
            {"callerInstallationId":"caller","targetInstallationId":"target","operations":["first","second","third"],"enabled":false}
            """;
    @Autowired MockMvc mvc;
    @MockitoBean PluginServicePermissionService service;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean CustomUserDetailsService users;

    @Test
    void rejectsAnonymousAndUsersWithoutTheWriteAuthority() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).with(user(principal(PermissionNames.PLUGIN_SERVICE_PERMISSION_READ)))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        mvc.perform(post(PATH).with(user(principal(PermissionNames.PLUGIN_RUNTIME_READ)))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void bindsTheBatchAndPrincipalAndReturnsTheStandardEnvelope() throws Exception {
        var writer = principal(PermissionNames.PLUGIN_SERVICE_PERMISSION_WRITE);
        var request = new BatchUpsertPluginServicePermissionRequest("caller", "target", List.of("first", "second", "third"), false);
        when(service.upsertBatch(request, writer)).thenReturn(List.of(
                new PluginServicePermissionResponse(1, "caller", "a", "target", "b", "first", false, null, null)));
        mvc.perform(post(PATH).with(user(writer)).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].operation").value("first"))
                .andExpect(jsonPath("$.data[0].enabled").value(false));
        verify(service).upsertBatch(request, writer);
    }

    @Test
    void invalidBatchUsesTheExistingBadRequestContract() throws Exception {
        when(service.upsertBatch(any(), any())).thenThrow(new IllegalArgumentException("At least one plugin service operation is required"));
        mvc.perform(post(PATH).with(user(principal(PermissionNames.PLUGIN_SERVICE_PERMISSION_WRITE)))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
    }

    private AuthenticatedUser principal(String authority) {
        return new AuthenticatedUser("writer", "writer", "", null, true, Set.of(), Set.of(authority));
    }
}
