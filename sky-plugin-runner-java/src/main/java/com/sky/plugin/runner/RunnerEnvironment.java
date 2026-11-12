package com.sky.plugin.runner;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

record RunnerEnvironment(
        String installationId,
        String pluginId,
        String instanceId,
        String mainClass,
        String bindHost,
        int port,
        int maxInboundMessageBytes,
        long maxTaskTimeoutMillis,
        String host,
        int hostPort,
        String runtimeToken,
        String workDir,
        Path pluginArtifactPath,
        List<Path> pluginDependencyPaths
) {
    static RunnerEnvironment from(String[] args, Map<String, String> env) {
        if (args == null || args.length < 1 || args[0] == null || args[0].isBlank()) {
            throw new IllegalArgumentException("Usage: java -jar sky-plugin-runner-java.jar <plugin-artifact.jar> [dependency.jar...]");
        }
        Path pluginArtifactPath = Path.of(args[0]).toAbsolutePath().normalize();
        List<Path> dependencyPaths = new ArrayList<>();
        for (int index = 1; index < args.length; index++) {
            if (args[index] == null || args[index].isBlank()) {
                throw new IllegalArgumentException("Plugin dependency path is blank");
            }
            dependencyPaths.add(Path.of(args[index]).toAbsolutePath().normalize());
        }
        return new RunnerEnvironment(
                required(env, "SKY_PLUGIN_INSTALLATION_ID"),
                required(env, "SKY_PLUGIN_ID"),
                required(env, "SKY_PLUGIN_INSTANCE_ID"),
                env.getOrDefault("SKY_PLUGIN_MAIN_CLASS", ""),
                env.getOrDefault("SKY_PLUGIN_GRPC_BIND_HOST", "127.0.0.1"),
                Integer.parseInt(required(env, "SKY_PLUGIN_PORT")),
                Integer.parseInt(env.getOrDefault("SKY_PLUGIN_GRPC_MAX_INBOUND_MESSAGE_BYTES", "1048576")),
                Long.parseLong(env.getOrDefault("SKY_PLUGIN_MAX_TASK_TIMEOUT_MILLIS", "120000")),
                env.getOrDefault("SKY_HOST_GRPC_HOST", "127.0.0.1"),
                Integer.parseInt(required(env, "SKY_HOST_GRPC_PORT")),
                required(env, "SKY_PLUGIN_TOKEN"),
                env.getOrDefault("SKY_PLUGIN_WORK_DIR", "."),
                pluginArtifactPath,
                List.copyOf(dependencyPaths)
        );
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing environment variable: " + name);
        }
        return value;
    }

}
