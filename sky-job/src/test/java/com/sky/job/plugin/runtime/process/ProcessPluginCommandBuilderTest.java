package com.sky.job.plugin.runtime.process;

import com.sky.job.plugin.runtime.PluginRuntimeSpec;
import com.sky.job.plugin.security.PluginCapability;
import com.sky.job.system.DynamicProcessPluginRuntimeSettings;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessPluginCommandBuilderTest {
    private final DynamicProcessPluginRuntimeSettings runtimeSettings = mock(DynamicProcessPluginRuntimeSettings.class);
    private final ProcessPluginRuntimeProperties properties = new ProcessPluginRuntimeProperties(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            "java",
            "256m",
            Path.of("runner.jar"),
            0,
            null,
            null,
            null
    );
    private final ProcessPluginCommandBuilder builder = new ProcessPluginCommandBuilder(properties, runtimeSettings);

    @Test
    void omitsJdwpArgumentWhenRemoteDebugIsDisabled() {
        when(runtimeSettings.maxHeap()).thenReturn("256m");

        List<String> command = builder.build(spec(false, 5005, true), 19000);

        assertThat(command).noneMatch(argument -> argument.startsWith("-agentlib:jdwp="));
    }

    @Test
    void addsSuspendingJdwpArgumentWhenRemoteDebugIsEnabled() {
        when(runtimeSettings.maxHeap()).thenReturn("256m");

        List<String> command = builder.build(spec(true, 5005, true), 19000);

        assertThat(command).contains("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:5005");
    }

    @Test
    void addsNonSuspendingJdwpArgumentWithCustomPort() {
        when(runtimeSettings.maxHeap()).thenReturn("256m");

        List<String> command = builder.build(spec(true, 5010, false), 19000);

        assertThat(command).contains("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5010");
    }

    private PluginRuntimeSpec spec(boolean remoteDebugEnabled, int remoteDebugPort, boolean remoteDebugSuspend) {
        return new PluginRuntimeSpec(
                "installation-a",
                "demo",
                "demo-instance",
                "demo.Plugin",
                Path.of("plugin.jar"),
                "sha",
                Path.of("work"),
                19090,
                List.of(),
                Set.of(PluginCapability.TASK),
                remoteDebugEnabled,
                remoteDebugPort,
                remoteDebugSuspend,
                "run",
                null,
                null
        );
    }
}
