package com.sky.job.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.plugin.script.*;
import com.sky.job.web.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Set;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=ScriptEnvironmentController.class)
@Import({SecurityConfig.class,JwtRequestFilter.class,RestAuthenticationEntryPoint.class,RestAccessDeniedHandler.class,ApiResponseAdvice.class,GlobalExceptionHandler.class})
class ScriptEnvironmentSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean ScriptEnvironmentService environments;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean CustomUserDetailsService users;
    @Test void dependenciesRequireAdminEvenWithPluginManagementPermission()throws Exception {
        mvc.perform(get("/plugins/script-environments/node")).andExpect(status().isUnauthorized());
        mvc.perform(get("/plugins/script-environments/node").with(user("operator").authorities(new SimpleGrantedAuthority(PermissionNames.PLUGIN_RUNTIME_MANAGE)))).andExpect(status().isForbidden());
        mvc.perform(post("/plugins/script-environments/node/operations").with(user("operator").authorities(new SimpleGrantedAuthority(PermissionNames.PLUGIN_RUNTIME_MANAGE))).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"install\",\"name\":\"axios\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(environments);
        var admin=new AuthenticatedUser("1","admin","",1L,true,Set.of("ADMIN"),Set.of());
        when(environments.submit("node","install","axios",null,"admin")).thenReturn(new ObjectMapper().createObjectNode().put("id","operation"));
        mvc.perform(post("/plugins/script-environments/node/operations").with(user(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"install\",\"name\":\"axios\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value("operation"));
    }
}
