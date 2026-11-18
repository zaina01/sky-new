package com.sky.job.plugin.script;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.plugin.runtime.PluginRuntimeRecord;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.job.scheduler.dto.*;
import com.sky.job.security.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InstallationScriptOptionsAuthorizationTest {
    @Test void requiresConfigPermissionsAndInstanceScopeBeforeReadingSecrets() {
        var store=mock(InstallationScriptOptionsStore.class);var runtimes=mock(PluginRuntimeMetadataStore.class);var scope=mock(DataScopeService.class);
        var service=new InstallationScriptOptionsService(store,runtimes,scope,null,new ObjectMapper());
        var jobOnly=new AuthenticatedUser("other","other","",2L,true,Set.of(),Set.of(PermissionNames.JOB_READ,PermissionNames.JOB_WRITE));
        assertThatThrownBy(()->service.arguments("a",jobOnly)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->service.environmentSettings("a",jobOnly)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        var user=new AuthenticatedUser("other","other","",2L,true,Set.of(),Set.of(PermissionNames.PLUGIN_CONFIG_READ,PermissionNames.PLUGIN_CONFIG_WRITE));
        var runtime=mock(PluginRuntimeRecord.class);when(runtime.ownerUserId()).thenReturn("owner");when(runtime.ownerDepartmentId()).thenReturn(1L);
        when(runtimes.findRuntimeByInstallationId("a")).thenReturn(Optional.of(runtime));
        assertThatThrownBy(()->service.arguments("a",user)).isInstanceOf(com.sky.job.web.ResourceNotFoundException.class);
        assertThatThrownBy(()->service.batch("a",List.of("x"),false,false,user)).isInstanceOf(com.sky.job.web.ResourceNotFoundException.class);
        assertThatThrownBy(()->service.environmentSettings("a",user)).isInstanceOf(com.sky.job.web.ResourceNotFoundException.class);
        assertThatThrownBy(()->service.saveEnvironmentSettings("a",new ObjectMapper().createObjectNode(),user)).isInstanceOf(com.sky.job.web.ResourceNotFoundException.class);
        verifyNoInteractions(store);
    }
    @Test void rejectsLegacyJobPayloadInsteadOfIgnoringIt()throws Exception {
        var json=new ObjectMapper();
        String body="{\"jobName\":\"x\",\"scriptOptions\":{\"arguments\":[\"--test\"],\"environment\":[]}}";
        assertThatThrownBy(()->json.readValue(body,CreateJobRequest.class)).hasMessageContaining("installation");
        assertThatThrownBy(()->json.readValue(body,UpdateJobRequest.class)).hasMessageContaining("installation");
        assertThatThrownBy(()->json.readValue("{\"scriptOptions\":\"legacy-value\"}",CreateJobRequest.class)).hasMessageContaining("installation");
        assertThat(json.writeValueAsString(json.readValue("{\"jobName\":\"x\"}",UpdateJobRequest.class))).doesNotContain("scriptOptions");
    }
    @Test void legacyPayloadGetsActionableHttpErrorWithoutEchoingSecrets()throws Exception {
        var service=mock(com.sky.job.scheduler.JobService.class);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new com.sky.job.scheduler.JobController(service))
                .setControllerAdvice(new com.sky.job.web.GlobalExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/jobs").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"scriptOptions\":{\"environment\":[{\"name\":\"token\",\"value\":\"do-not-echo\"}]}}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("installation")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("do-not-echo"))));
        verifyNoInteractions(service);
    }
}
