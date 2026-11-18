package com.sky.job.plugin.script;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties("sky.plugin.script")
public record ScriptRuntimeProperties(Path root, String nodeCommand, String pythonCommand, Path npmCli,
        Path nodeRunner, Path pythonRunner, int maxProcesses, long maxLogBytes, Duration installTimeout) {
    public ScriptRuntimeProperties {
        if (root == null) root = Path.of("./data/plugins/script-environments");
        if (nodeCommand == null || nodeCommand.isBlank()) nodeCommand = "node";
        if (pythonCommand == null || pythonCommand.isBlank()) pythonCommand = "python";
        if (npmCli != null && npmCli.toString().isBlank()) npmCli = null;
        if (nodeRunner == null) nodeRunner = Path.of("./sky-plugin-runner-node/runner.mjs");
        if (pythonRunner == null) pythonRunner = Path.of("./sky-plugin-runner-python/runner.py");
        if (maxProcesses <= 0) maxProcesses = 8;
        if (maxLogBytes <= 0) maxLogBytes = 10 * 1024 * 1024;
        if (installTimeout == null) installTimeout = Duration.ofMinutes(10);
    }
}
