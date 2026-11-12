package com.sky.job.security;

import com.sky.job.plugin.management.PluginManagementController;
import com.sky.job.plugin.management.PluginManagementService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PluginManagementController.class)
@Import({SecurityConfig.class, JwtRequestFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, ApiResponseAdvice.class, GlobalExceptionHandler.class})
class PluginAccountGroupSecurityContractTest {
    private static final String BASE = "/plugins/installations/installation-a";
    @Autowired MockMvc mvc;
    @MockitoBean PluginManagementService management;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean CustomUserDetailsService users;

    @Test
    void readingGroupsRequiresAccountReadRatherThanRuntimeRead() throws Exception {
        mvc.perform(get(BASE + "/account-groups").with(user("reader").authorities(new SimpleGrantedAuthority(PermissionNames.PLUGIN_ACCOUNT_READ))))
                .andExpect(status().isOk());
        mvc.perform(get(BASE + "/account-groups").with(user("runtime-reader").authorities(new SimpleGrantedAuthority(PermissionNames.PLUGIN_RUNTIME_READ))))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/account-groups")).andExpect(status().isUnauthorized());
    }

    @Test
    void allGroupMutationsRequireAccountWrite() throws Exception {
        for (MockHttpServletRequestBuilder request : mutations()) {
            mvc.perform(request.with(user("reader").authorities(new SimpleGrantedAuthority(PermissionNames.PLUGIN_ACCOUNT_READ))))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(management);
        for (MockHttpServletRequestBuilder request : mutations()) {
            mvc.perform(request.with(user("writer").authorities(new SimpleGrantedAuthority(PermissionNames.PLUGIN_ACCOUNT_WRITE))))
                    .andExpect(status().isOk());
        }
    }

    private List<MockHttpServletRequestBuilder> mutations() {
        return List.of(
                post(BASE + "/account-groups").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Primary\"}"),
                put(BASE + "/account-groups/10").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Backup\"}"),
                delete(BASE + "/account-groups/10"),
                patch(BASE + "/accounts/a/group").contentType(MediaType.APPLICATION_JSON).content("{\"groupId\":null}"),
                patch(BASE + "/accounts/group").contentType(MediaType.APPLICATION_JSON).content("{\"accountIds\":[\"a\"],\"groupId\":10}")
        );
    }
}
