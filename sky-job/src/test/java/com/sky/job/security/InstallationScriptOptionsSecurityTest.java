package com.sky.job.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.plugin.script.*;
import com.sky.job.web.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Set;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=InstallationScriptOptionsController.class)
@Import({SecurityConfig.class,JwtRequestFilter.class,RestAuthenticationEntryPoint.class,RestAccessDeniedHandler.class,ApiResponseAdvice.class,GlobalExceptionHandler.class})
class InstallationScriptOptionsSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean InstallationScriptOptionsService service;
    @MockitoBean JwtTokenService tokens;
    @MockitoBean CustomUserDetailsService users;
    AuthenticatedUser actor(String permission){return new AuthenticatedUser("1","operator","",1L,true,Set.of(),Set.of(permission));}
    @Test void usesConfigPermissionsRatherThanJobOrRuntimePermissions()throws Exception {
        clearInvocations(service); // ApplicationReadyEvent invokes the legacy-data check during context startup.
        String path="/plugins/installations/a/script-arguments";
        mvc.perform(get(path).with(user(actor(PermissionNames.JOB_READ)))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(user(actor(PermissionNames.PLUGIN_RUNTIME_READ)))).andExpect(status().isForbidden());
        mvc.perform(put(path).with(user(actor(PermissionNames.PLUGIN_CONFIG_READ))).contentType(MediaType.APPLICATION_JSON).content("{\"arguments\":[]}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
        var reader=actor(PermissionNames.PLUGIN_CONFIG_READ);
        when(service.arguments("a",reader)).thenReturn(new ObjectMapper().createObjectNode().put("revision","2"));
        mvc.perform(get(path).with(user(reader))).andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value("2"));
        var writer=actor(PermissionNames.PLUGIN_CONFIG_WRITE);
        mvc.perform(post("/plugins/installations/a/script-environment-variables/batch-enabled").with(user(writer)).contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"v1\"],\"enabled\":false}")).andExpect(status().isOk());
        verify(service).batch("a",java.util.List.of("v1"),false,false,writer);
    }
    @Test void mergeSettingsRequireConfigReadAndWrite()throws Exception {
        clearInvocations(service);
        String path="/plugins/installations/a/script-environment-settings";
        var json=new ObjectMapper();
        var request=json.readTree("{\"defaultSeparator\":\"@\",\"overrides\":[]}");
        mvc.perform(get(path).with(user(actor(PermissionNames.JOB_READ)))).andExpect(status().isForbidden());
        mvc.perform(put(path).with(user(actor(PermissionNames.PLUGIN_CONFIG_READ))).contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
        var reader=actor(PermissionNames.PLUGIN_CONFIG_READ);
        when(service.environmentSettings("a",reader)).thenReturn(json.createObjectNode().put("revision","4"));
        mvc.perform(get(path).with(user(reader))).andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value("4"));
        var writer=actor(PermissionNames.PLUGIN_CONFIG_WRITE);
        mvc.perform(put(path).with(user(writer)).contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isOk());
        verify(service).saveEnvironmentSettings("a",request,writer);
    }
}
