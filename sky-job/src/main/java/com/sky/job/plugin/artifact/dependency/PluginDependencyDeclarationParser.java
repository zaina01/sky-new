package com.sky.job.plugin.artifact.dependency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

@Component
@EnableConfigurationProperties(PluginDependencyResolutionProperties.class)
public class PluginDependencyDeclarationParser {
    static final String DEPENDENCIES_ENTRY = "META-INF/sky-plugin-dependencies.json";
    private static final Pattern SEGMENT_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final int MAX_SEGMENT_LENGTH = 128;
    private static final Set<String> DYNAMIC_VERSIONS = Set.of("LATEST", "RELEASE", "+");

    private final ObjectMapper objectMapper;
    private final PluginDependencyResolutionProperties properties;

    public PluginDependencyDeclarationParser(ObjectMapper objectMapper, PluginDependencyResolutionProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public boolean hasDependencyDescriptor(Path artifact) {
        try (ZipFile zipFile = new ZipFile(artifact.toFile())) {
            return zipFile.getEntry(DEPENDENCIES_ENTRY) != null;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to inspect plugin dependency descriptor", exception);
        }
    }

    public List<PluginDependencyDeclaration> parse(Path artifact) {
        try (ZipFile zipFile = new ZipFile(artifact.toFile())) {
            var entry = zipFile.getEntry(DEPENDENCIES_ENTRY);
            if (entry == null) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(zipFile.getInputStream(entry));
            JsonNode dependencies = root.path("dependencies");
            if (!root.isObject() || !dependencies.isArray()) {
                throw new IllegalArgumentException("Plugin dependency descriptor must contain dependencies array");
            }
            List<String> coordinates = new ArrayList<>();
            for (JsonNode dependency : dependencies) {
                if (!dependency.isTextual()) {
                    throw new IllegalArgumentException("Plugin dependency coordinate must be a string");
                }
                coordinates.add(dependency.asText());
            }
            return parseCoordinates(coordinates);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to parse plugin dependency descriptor", exception);
        }
    }

    public List<PluginDependencyDeclaration> parseCoordinates(List<String> dependencyCoordinates) {
        if (dependencyCoordinates == null || dependencyCoordinates.isEmpty()) {
            return List.of();
        }
        if (dependencyCoordinates.size() > properties.maxDeclaredDependencies()) {
            throw new IllegalArgumentException("Plugin declares too many dependencies");
        }
        Set<String> coordinates = new LinkedHashSet<>();
        for (String value : dependencyCoordinates) {
            String coordinate = value == null ? "" : value.trim();
            validateCoordinate(coordinate);
            coordinates.add(coordinate);
        }
        List<PluginDependencyDeclaration> result = new ArrayList<>();
        for (String coordinate : coordinates) {
            String[] parts = coordinate.split(":", -1);
            result.add(new PluginDependencyDeclaration(parts[0], parts[1], parts[2]));
        }
        return List.copyOf(result);
    }

    private void validateCoordinate(String coordinate) {
        String[] parts = coordinate.split(":", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Plugin dependency coordinate must use groupId:artifactId:version");
        }
        for (String part : parts) {
            if (part.isBlank() || part.length() > MAX_SEGMENT_LENGTH || !SEGMENT_PATTERN.matcher(part).matches()) {
                throw new IllegalArgumentException("Invalid plugin dependency coordinate");
            }
        }
        String version = parts[2];
        if (DYNAMIC_VERSIONS.contains(version) || version.contains("[") || version.contains("]") || version.contains("(") || version.contains(")")) {
            throw new IllegalArgumentException("Dynamic plugin dependency versions are not supported");
        }
        if (!properties.allowSnapshots() && version.endsWith("-SNAPSHOT")) {
            throw new IllegalArgumentException("Snapshot plugin dependencies are not allowed");
        }
        if (isForbiddenPlatformDependency(parts[0], parts[1])) {
            throw new IllegalArgumentException("Platform dependencies must not be declared by plugins");
        }
    }

    private boolean isForbiddenPlatformDependency(String groupId, String artifactId) {
        return groupId.equals("io.github.zaina01") && (artifactId.equals("sky-plugin-api") || artifactId.equals("sky-plugin-protocol") || artifactId.equals("sky-plugin-runner-java"))
                || groupId.equals("io.grpc");
    }
}
