package com.sky.job.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.authorization.AuthorizationManagers.allOf;
import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SkySecurityProperties.class)
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtRequestFilter jwtRequestFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/user/register", "/user/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/user/me").hasAuthority(PermissionNames.USER_READ_SELF)
                        .requestMatchers(HttpMethod.POST, "/user/change-password").hasAuthority(PermissionNames.USER_PASSWORD_CHANGE_SELF)
                        .requestMatchers(HttpMethod.GET, "/system/users", "/system/users/**").hasAuthority(PermissionNames.SYSTEM_USER_READ)
                        .requestMatchers(HttpMethod.POST, "/system/users/*/password/reset").hasAuthority(PermissionNames.SYSTEM_USER_PASSWORD_RESET)
                        .requestMatchers(HttpMethod.POST, "/system/users").hasAuthority(PermissionNames.SYSTEM_USER_WRITE)
                        .requestMatchers(HttpMethod.PUT, "/system/users/*/roles").hasAuthority(PermissionNames.SYSTEM_USER_WRITE)
                        .requestMatchers(HttpMethod.PUT, "/system/users/*").hasAuthority(PermissionNames.SYSTEM_USER_WRITE)
                        .requestMatchers(HttpMethod.PATCH, "/system/users/*/enabled").hasAuthority(PermissionNames.SYSTEM_USER_WRITE)
                        .requestMatchers(HttpMethod.DELETE, "/system/users/*").hasAuthority(PermissionNames.SYSTEM_USER_DELETE)
                        .requestMatchers(HttpMethod.GET, "/system/roles", "/system/roles/**").hasAuthority(PermissionNames.SYSTEM_ROLE_READ)
                        .requestMatchers(HttpMethod.POST, "/system/roles").hasAuthority(PermissionNames.SYSTEM_ROLE_WRITE)
                        .requestMatchers(HttpMethod.PUT, "/system/roles/*/permissions").hasAuthority(PermissionNames.SYSTEM_ROLE_WRITE)
                        .requestMatchers(HttpMethod.PUT, "/system/roles/*/data-scope").hasAuthority(PermissionNames.SYSTEM_ROLE_WRITE)
                        .requestMatchers(HttpMethod.PUT, "/system/roles/*").hasAuthority(PermissionNames.SYSTEM_ROLE_WRITE)
                        .requestMatchers(HttpMethod.DELETE, "/system/roles/*").hasAuthority(PermissionNames.SYSTEM_ROLE_DELETE)
                        .requestMatchers(HttpMethod.GET, "/system/permissions", "/system/permissions/**").hasAuthority(PermissionNames.SYSTEM_PERMISSION_READ)
                        .requestMatchers(HttpMethod.GET, "/system/departments", "/system/departments/**").hasAuthority(PermissionNames.SYSTEM_DEPARTMENT_READ)
                        .requestMatchers(HttpMethod.POST, "/system/departments").hasAuthority(PermissionNames.SYSTEM_DEPARTMENT_WRITE)
                        .requestMatchers(HttpMethod.PUT, "/system/departments/*").hasAuthority(PermissionNames.SYSTEM_DEPARTMENT_WRITE)
                        .requestMatchers(HttpMethod.PATCH, "/system/departments/*/enabled").hasAuthority(PermissionNames.SYSTEM_DEPARTMENT_WRITE)
                        .requestMatchers(HttpMethod.DELETE, "/system/departments/*").hasAuthority(PermissionNames.SYSTEM_DEPARTMENT_DELETE)
                        .requestMatchers(HttpMethod.POST, "/system/data-ownership/transfer").hasAuthority(PermissionNames.SYSTEM_DATA_OWNERSHIP_TRANSFER)
                        .requestMatchers(HttpMethod.GET, "/system/overview").hasAuthority(PermissionNames.SYSTEM_OVERVIEW_READ)
                        .requestMatchers(HttpMethod.GET, "/system/login-logs", "/system/login-logs/**").hasAuthority(PermissionNames.SYSTEM_LOGIN_LOG_READ)
                        .requestMatchers(HttpMethod.GET, "/system/logs", "/system/logs/**").hasAuthority(PermissionNames.SYSTEM_LOG_READ)
                        .requestMatchers(HttpMethod.POST, "/system/retention-cleanup").hasAuthority(PermissionNames.SYSTEM_RETENTION_CLEANUP)
                        .requestMatchers(HttpMethod.GET, "/system/settings", "/system/settings/**").hasAuthority(PermissionNames.SYSTEM_SETTING_READ)
                        .requestMatchers(HttpMethod.PUT, "/system/settings", "/system/settings/**").hasAuthority(PermissionNames.SYSTEM_SETTING_WRITE)
                        .requestMatchers(HttpMethod.POST, "/notifications/channels/*/test").hasAuthority(PermissionNames.NOTIFICATION_CHANNEL_TEST)
                        .requestMatchers(HttpMethod.GET, "/notifications/rules", "/notifications/rules/**").hasAuthority(PermissionNames.NOTIFICATION_RULE_READ)
                        .requestMatchers(HttpMethod.POST, "/notifications/rules").hasAuthority(PermissionNames.NOTIFICATION_RULE_WRITE)
                        .requestMatchers(HttpMethod.PUT, "/notifications/rules/*").hasAuthority(PermissionNames.NOTIFICATION_RULE_WRITE)
                        .requestMatchers(HttpMethod.PATCH, "/notifications/rules/*/enabled").hasAuthority(PermissionNames.NOTIFICATION_RULE_WRITE)
                        .requestMatchers(HttpMethod.DELETE, "/notifications/rules/*").hasAuthority(PermissionNames.NOTIFICATION_RULE_WRITE)
                        .requestMatchers(HttpMethod.GET, "/notifications/channels", "/notifications/channels/**").hasAuthority(PermissionNames.NOTIFICATION_CHANNEL_READ)
                        .requestMatchers(HttpMethod.POST, "/notifications/channels").hasAuthority(PermissionNames.NOTIFICATION_CHANNEL_WRITE)
                        .requestMatchers(HttpMethod.PUT, "/notifications/channels/*").hasAuthority(PermissionNames.NOTIFICATION_CHANNEL_WRITE)
                        .requestMatchers(HttpMethod.PATCH, "/notifications/channels/*/enabled").hasAuthority(PermissionNames.NOTIFICATION_CHANNEL_WRITE)
                        .requestMatchers(HttpMethod.DELETE, "/notifications/channels/*").hasAuthority(PermissionNames.NOTIFICATION_CHANNEL_WRITE)
                        .requestMatchers(HttpMethod.POST, "/notifications/history/*/retry").hasAuthority(PermissionNames.NOTIFICATION_HISTORY_RETRY)
                        .requestMatchers(HttpMethod.GET, "/notifications/history", "/notifications/history/**").hasAuthority(PermissionNames.NOTIFICATION_HISTORY_READ)
                        .requestMatchers(HttpMethod.POST, "/plugins/artifacts").hasAuthority(PermissionNames.PLUGIN_ARTIFACT_UPLOAD)
                        .requestMatchers(HttpMethod.GET, "/plugins/dependencies/local-artifacts").hasAuthority(PermissionNames.PLUGIN_RUNTIME_READ)
                        .requestMatchers(HttpMethod.POST, "/plugins/dependencies/local-artifacts").hasAuthority(PermissionNames.PLUGIN_RUNTIME_MANAGE)
                        .requestMatchers(HttpMethod.GET, "/plugins/options").hasAuthority(PermissionNames.PLUGIN_RUNTIME_READ)
                        .requestMatchers(HttpMethod.GET, "/plugins/definitions", "/plugins/definitions/*").hasAuthority(PermissionNames.PLUGIN_RUNTIME_READ)
                        .requestMatchers(HttpMethod.DELETE, "/plugins/definitions/*").hasAuthority(PermissionNames.PLUGIN_RUNTIME_MANAGE)
                        .requestMatchers(HttpMethod.GET, "/plugins/definitions/*/dependencies").hasAuthority(PermissionNames.PLUGIN_RUNTIME_READ)
                        .requestMatchers(HttpMethod.POST, "/plugins/definitions/*/dependencies/install", "/plugins/definitions/*/dependencies/reinstall").hasAuthority(PermissionNames.PLUGIN_RUNTIME_MANAGE)
                        .requestMatchers(HttpMethod.DELETE, "/plugins/definitions/*/dependencies").hasAuthority(PermissionNames.PLUGIN_RUNTIME_MANAGE)
                        .requestMatchers(HttpMethod.GET, "/plugins/audit-logs", "/plugins/audit-logs/**").hasAuthority(PermissionNames.PLUGIN_AUDIT_READ)
                        .requestMatchers(HttpMethod.GET, "/plugin-service/calls", "/plugin-service/calls/**").hasAuthority(PermissionNames.PLUGIN_AUDIT_READ)
                        .requestMatchers(HttpMethod.GET, "/plugin-service/permissions", "/plugin-service/permissions/**").hasAuthority(PermissionNames.PLUGIN_SERVICE_PERMISSION_READ)
                        .requestMatchers(HttpMethod.POST, "/plugin-service/permissions", "/plugin-service/permissions/batch").hasAuthority(PermissionNames.PLUGIN_SERVICE_PERMISSION_WRITE)
                        .requestMatchers(HttpMethod.PATCH, "/plugin-service/permissions/*/enabled").hasAuthority(PermissionNames.PLUGIN_SERVICE_PERMISSION_WRITE)
                        .requestMatchers(HttpMethod.DELETE, "/plugin-service/permissions/*").hasAuthority(PermissionNames.PLUGIN_SERVICE_PERMISSION_WRITE)
                        .requestMatchers(HttpMethod.GET, "/plugins/installations/*/config").hasAuthority(PermissionNames.PLUGIN_CONFIG_READ)
                        .requestMatchers(HttpMethod.PUT, "/plugins/installations/*/config").hasAuthority(PermissionNames.PLUGIN_CONFIG_WRITE)
                        .requestMatchers(HttpMethod.GET, "/plugins/installations/*/accounts/**").hasAuthority(PermissionNames.PLUGIN_ACCOUNT_READ)
                        .requestMatchers(HttpMethod.GET, "/plugins/installations/*/account-groups").hasAuthority(PermissionNames.PLUGIN_ACCOUNT_READ)
                        .requestMatchers(HttpMethod.POST, "/plugins/installations/*/account-groups").hasAuthority(PermissionNames.PLUGIN_ACCOUNT_WRITE)
                        .requestMatchers(HttpMethod.PUT, "/plugins/installations/*/account-groups/*").hasAuthority(PermissionNames.PLUGIN_ACCOUNT_WRITE)
                        .requestMatchers(HttpMethod.DELETE, "/plugins/installations/*/account-groups/*").hasAuthority(PermissionNames.PLUGIN_ACCOUNT_WRITE)
                        .requestMatchers(HttpMethod.PATCH, "/plugins/installations/*/accounts/group", "/plugins/installations/*/accounts/*/group").hasAuthority(PermissionNames.PLUGIN_ACCOUNT_WRITE)
                        .requestMatchers(HttpMethod.PUT, "/plugins/installations/*/accounts/view-preference").hasAuthority(PermissionNames.PLUGIN_ACCOUNT_READ)
                        .requestMatchers(HttpMethod.POST, "/plugins/installations/*/accounts/import").hasAuthority(PermissionNames.PLUGIN_ACCOUNT_WRITE)
                        .requestMatchers(HttpMethod.POST, "/plugins/installations/*/accounts").hasAuthority(PermissionNames.PLUGIN_ACCOUNT_WRITE)
                        .requestMatchers(HttpMethod.PUT, "/plugins/installations/*/accounts/*").hasAuthority(PermissionNames.PLUGIN_ACCOUNT_WRITE)
                        .requestMatchers(HttpMethod.PATCH, "/plugins/installations/*/accounts/enabled").hasAuthority(PermissionNames.PLUGIN_ACCOUNT_WRITE)
                        .requestMatchers(HttpMethod.PATCH, "/plugins/installations/*/accounts/*/enabled").hasAuthority(PermissionNames.PLUGIN_ACCOUNT_WRITE)
                        .requestMatchers(HttpMethod.DELETE, "/plugins/installations/*/accounts", "/plugins/installations/*/accounts/*").hasAuthority(PermissionNames.PLUGIN_ACCOUNT_WRITE)
                        .requestMatchers(HttpMethod.GET, "/plugins/installations/*/notification-channels")
                        .access(allOf(
                                hasAuthority(PermissionNames.PLUGIN_RUNTIME_MANAGE),
                                hasAuthority(PermissionNames.PLUGIN_NOTIFICATION_CHANNEL_BIND),
                                hasAuthority(PermissionNames.NOTIFICATION_CHANNEL_READ)))
                        .requestMatchers(HttpMethod.PUT, "/plugins/installations/*/notification-channels")
                        .access(allOf(
                                hasAuthority(PermissionNames.PLUGIN_RUNTIME_MANAGE),
                                hasAuthority(PermissionNames.PLUGIN_NOTIFICATION_CHANNEL_BIND),
                                hasAuthority(PermissionNames.NOTIFICATION_CHANNEL_READ)))
                        .requestMatchers(HttpMethod.POST, "/plugins/installations/*/start", "/plugins/installations/*/stop", "/plugins/installations/*/restart").hasAuthority(PermissionNames.PLUGIN_RUNTIME_MANAGE)
                        .requestMatchers(HttpMethod.PUT, "/plugins/installations/*/debug").hasAuthority(PermissionNames.PLUGIN_RUNTIME_MANAGE)
                        .requestMatchers(HttpMethod.DELETE, "/plugins/installations/*").hasAuthority(PermissionNames.PLUGIN_RUNTIME_MANAGE)
                        .requestMatchers(HttpMethod.POST, "/plugins/installations").hasAuthority(PermissionNames.PLUGIN_RUNTIME_MANAGE)
                        .requestMatchers(HttpMethod.GET, "/plugins/installations", "/plugins/installations/**").hasAuthority(PermissionNames.PLUGIN_RUNTIME_READ)
                        .requestMatchers(HttpMethod.GET, "/jobs/*/executions").hasAuthority(PermissionNames.JOB_EXECUTION_READ)
                        .requestMatchers(HttpMethod.GET, "/jobs/**").hasAuthority(PermissionNames.JOB_READ)
                        .requestMatchers(HttpMethod.POST, "/jobs/*/run").hasAuthority(PermissionNames.JOB_RUN)
                        .requestMatchers(HttpMethod.POST, "/jobs/**").hasAuthority(PermissionNames.JOB_WRITE)
                        .requestMatchers(HttpMethod.PUT, "/jobs/*/notification-channels")
                        .access(allOf(
                                hasAuthority(PermissionNames.JOB_WRITE),
                                hasAuthority(PermissionNames.JOB_NOTIFICATION_BIND),
                                hasAuthority(PermissionNames.NOTIFICATION_CHANNEL_READ)))
                        .requestMatchers(HttpMethod.PUT, "/jobs/**").hasAuthority(PermissionNames.JOB_WRITE)
                        .requestMatchers(HttpMethod.DELETE, "/jobs/**").hasAuthority(PermissionNames.JOB_DELETE)
                        .requestMatchers(HttpMethod.POST, "/job-executions/*/cancel").hasAuthority(PermissionNames.JOB_EXECUTION_CANCEL)
                        .requestMatchers(HttpMethod.GET, "/job-executions/**").hasAuthority(PermissionNames.JOB_EXECUTION_READ)
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(CustomUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
