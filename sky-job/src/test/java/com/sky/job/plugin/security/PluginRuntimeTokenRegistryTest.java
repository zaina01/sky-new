package com.sky.job.plugin.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PluginRuntimeTokenRegistryTest {
    @Test
    void authenticatedPrincipalKeepsInstallationJobAndExecutionBinding() {
        PluginRuntimeTokenRegistry registry = new PluginRuntimeTokenRegistry();
        String token = registry.register(
                "installation-a",
                "demo",
                "runner-a",
                42L,
                420L,
                Set.of(PluginCapability.TASK, PluginCapability.NOTIFICATION),
                Duration.ofMinutes(1)
        );

        PluginRuntimePrincipal principal = registry.authenticate("demo", "runner-a", token).orElseThrow();

        assertThat(principal.installationId()).isEqualTo("installation-a");
        assertThat(principal.jobId()).isEqualTo(42L);
        assertThat(principal.executionId()).isEqualTo(420L);
        assertThat(principal.taskBound()).isTrue();
    }

    @Test
    void tokenCannotBeReplayedAsAnotherInstallationRunner() {
        PluginRuntimeTokenRegistry registry = new PluginRuntimeTokenRegistry();
        String firstToken = registry.register(
                "installation-a", "demo", "runner-a", 42L, 420L,
                Set.of(PluginCapability.TASK), Duration.ofMinutes(1));
        String secondToken = registry.register(
                "installation-b", "demo", "runner-b", 43L, 430L,
                Set.of(PluginCapability.TASK), Duration.ofMinutes(1));

        assertThat(registry.authenticate("demo", "runner-b", firstToken)).isEmpty();
        assertThat(registry.authenticate("demo", "runner-a", secondToken)).isEmpty();
        assertThat(registry.authenticate("demo", "runner-a", firstToken))
                .get().extracting(PluginRuntimePrincipal::installationId).isEqualTo("installation-a");
        assertThat(registry.authenticate("demo", "runner-b", secondToken))
                .get().extracting(PluginRuntimePrincipal::installationId).isEqualTo("installation-b");
    }
}
