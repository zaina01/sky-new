package com.sky.job.security;

import com.sky.job.web.ApiResponseAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DeploymentHealthTest.TestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:deployment-health",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.quartz.auto-startup=false"
})
@AutoConfigureMockMvc
class DeploymentHealthTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({SecurityConfig.class, JwtRequestFilter.class, RestAuthenticationEntryPoint.class,
            RestAccessDeniedHandler.class, ApiResponseAdvice.class})
    static class TestApplication {
    }

    @Autowired
    MockMvc mvc;
    @MockitoBean
    JwtTokenService jwtTokenService;
    @MockitoBean
    CustomUserDetailsService userDetailsService;
    @MockitoSpyBean
    DataSource dataSource;

    @Test
    void healthIsPublicAndUnwrappedWithoutExposingComponents() throws Exception {
        mvc.perform(get("/actuator/health").accept("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void businessAndOtherActuatorPathsStillRequireAuthentication() throws Exception {
        mvc.perform(get("/user/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void databaseOutageIsUnhealthyWithoutLeakingConnectionDetails() throws Exception {
        doThrow(new SQLException("private database connection details")).when(dataSource).getConnection();
        mvc.perform(get("/actuator/health").accept("application/json"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }
}
