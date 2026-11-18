package com.sky.job.plugin.runtime.process;

import com.sky.job.plugin.artifact.Sha256Digests;
import com.sky.job.plugin.artifact.dependency.PluginDependencyResolutionProperties;
import com.sky.job.plugin.runtime.PluginRuntimeSpec;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ProcessPluginRuntimeValidator {
    private final ProcessPluginRuntimeProperties properties;
    private final PluginDependencyResolutionProperties dependencyProperties;

    public ProcessPluginRuntimeValidator(ProcessPluginRuntimeProperties properties, PluginDependencyResolutionProperties dependencyProperties) {
        this.properties = properties;
        this.dependencyProperties = dependencyProperties;
    }

    public void validate(PluginRuntimeSpec spec) {
        Path runnerJar = properties.runnerJar().toAbsolutePath().normalize();
        if ("java".equals(spec.runtimeEngine()) && (!Files.isRegularFile(runnerJar) || !runnerJar.getFileName().toString().endsWith(".jar"))) {
            throw new IllegalArgumentException("Plugin runner jar is not a regular jar file: " + runnerJar);
        }
        Path artifact = requireUnderRoot(spec.artifactPath(), properties.artifactRoot(), "artifact");
        Path workDir = spec.workDir().toAbsolutePath().normalize();
        requireUnderRoot(workDir, properties.workRoot(), "work directory");
        if (!Files.isRegularFile(artifact)) {
            throw new IllegalArgumentException("Plugin artifact is not a regular file");
        }
        String digest = Sha256Digests.digestHex(artifact);
        if (spec.artifactSha256() == null || !digest.equalsIgnoreCase(spec.artifactSha256())) {
            throw new IllegalArgumentException("Plugin artifact SHA-256 mismatch");
        }
        validateDependencies(spec);
    }

    private void validateDependencies(PluginRuntimeSpec spec) {
        Path repositoryRoot = dependencyProperties.repositoryRoot().toAbsolutePath().normalize();
        for (Path dependencyPath : spec.dependencyPaths()) {
            Path dependency = dependencyPath.toAbsolutePath().normalize();
            if (!dependency.startsWith(repositoryRoot) || !Files.isRegularFile(dependency) || !dependency.getFileName().toString().endsWith(".jar")) {
                throw new IllegalArgumentException("Plugin dependency jar is not available: " + dependency);
            }
        }
    }

    public Path logFile(PluginRuntimeSpec spec, String runId) {
        return logFilePath(spec.pluginId(), spec.instanceId(), runId);
    }

    public Path logFilePath(String pluginId, String instanceId, String runId) {
        Path logRoot = properties.logRoot().toAbsolutePath().normalize();
        Path logFile = logRoot.resolve(pluginId).resolve(instanceId).resolve(runId).resolve("runner.log").normalize();
        logFile = requireUnderRoot(logFile, logRoot, "log file");
        try {
            Files.createDirectories(logFile.getParent());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create plugin log directory", exception);
        }
        return logFile;
    }

    private Path requireUnderRoot(Path candidate, Path root, String label) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Plugin " + label + " escapes configured root");
        }
        return normalizedCandidate;
    }
}
