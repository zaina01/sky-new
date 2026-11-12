package com.sky.job.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigRouteTest {
    @Test
    void sensitiveDataPermissionMutationRoutesHaveExplicitAuthorities() throws Exception {
        String source = Files.readString(findSource(), StandardCharsets.UTF_8);

        assertThat(source)
                .contains(".requestMatchers(HttpMethod.PUT, \"/system/roles/*/data-scope\").hasAuthority(PermissionNames.SYSTEM_ROLE_WRITE)")
                .contains(".requestMatchers(HttpMethod.POST, \"/system/data-ownership/transfer\").hasAuthority(PermissionNames.SYSTEM_DATA_OWNERSHIP_TRANSFER)")
                .contains(".requestMatchers(HttpMethod.PUT, \"/plugins/installations/*/accounts/view-preference\").hasAuthority(PermissionNames.PLUGIN_ACCOUNT_READ)")
                .contains(".requestMatchers(HttpMethod.DELETE, \"/plugins/installations/*/accounts\", \"/plugins/installations/*/accounts/*\").hasAuthority(PermissionNames.PLUGIN_ACCOUNT_WRITE)")
                .contains(".requestMatchers(HttpMethod.GET, \"/plugins/installations/*/notification-channels\")")
                .contains("hasAuthority(PermissionNames.PLUGIN_NOTIFICATION_CHANNEL_BIND)")
                .contains("hasAuthority(PermissionNames.JOB_NOTIFICATION_BIND)");
    }

    private Path findSource() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path source = current.resolve("sky-job/src/main/java/com/sky/job/security/SecurityConfig.java");
            if (Files.isRegularFile(source)) return source;
            current = current.getParent();
        }
        throw new IllegalStateException("SecurityConfig.java not found");
    }
}
