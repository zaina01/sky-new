package com.sky.job.plugin.artifact.dependency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.plugin.artifact.PluginArtifactRegistry;
import com.sky.job.plugin.artifact.PluginDefinition;
import com.sky.job.plugin.artifact.dependency.dto.PluginDependencyInstallRequest;
import com.sky.job.plugin.artifact.dependency.dto.PluginDependencyInstallResponse;
import com.sky.job.plugin.artifact.dependency.dto.PluginDependencyResponse;
import com.sky.job.plugin.artifact.dependency.dto.PluginDependencyUninstallRequest;
import com.sky.job.plugin.storage.PluginAuditStore;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PluginDependencyManagementService {
    private final PluginDependencyDeclarationParser declarationParser;
    private final PluginMavenDependencyResolver dependencyResolver;
    private final PluginArtifactRegistry registry;
    private final PluginAuditStore auditStore;
    private final ObjectMapper objectMapper;

    public PluginDependencyManagementService(
            PluginDependencyDeclarationParser declarationParser,
            PluginMavenDependencyResolver dependencyResolver,
            PluginArtifactRegistry registry,
            PluginAuditStore auditStore,
            ObjectMapper objectMapper
    ) {
        this.declarationParser = declarationParser;
        this.dependencyResolver = dependencyResolver;
        this.registry = registry;
        this.auditStore = auditStore;
        this.objectMapper = objectMapper;
    }

    public List<PluginDependencyResponse> list(String pluginId) {
        PluginDefinition definition = definition(pluginId);
        return parseStoredDependencies(definition.dependencyJarsJson());
    }

    public PluginDependencyInstallResponse install(String pluginId, PluginDependencyInstallRequest request, String actor) {
        PluginDefinition definition = definition(pluginId);
        String mode = mode(request == null ? null : request.mode());
        List<PluginDependencyDeclaration> declarations = declarationParser.parseCoordinates(request == null ? null : request.dependencies());
        List<ResolvedPluginDependency> resolved = dependencyResolver.resolve(declarations);
        List<ResolvedPluginDependency> stored = "replace".equals(mode) ? resolved : merge(parseResolvedDependencies(definition.dependencyJarsJson()), resolved);
        registry.updateDependencies(pluginId, stored);
        auditStore.insert("", pluginId, "", actor, "PLUGIN_DEPENDENCY_INSTALL", auditDetail(declarations.size(), resolved.size(), stored.size(), mode));
        return response(pluginId, declarations.size(), stored);
    }

    public PluginDependencyInstallResponse reinstall(String pluginId, String actor) {
        PluginDefinition definition = definition(pluginId);
        List<PluginDependencyDeclaration> declarations = declarationParser.parse(Path.of(definition.artifactPath()));
        List<ResolvedPluginDependency> resolved = dependencyResolver.resolve(declarations);
        registry.updateDependencies(pluginId, resolved);
        auditStore.insert("", pluginId, "", actor, "PLUGIN_DEPENDENCY_REINSTALL", auditDetail(declarations.size(), resolved.size(), resolved.size(), "replace"));
        return response(pluginId, declarations.size(), resolved);
    }

    public PluginDependencyInstallResponse uninstall(String pluginId, PluginDependencyUninstallRequest request, String actor) {
        PluginDefinition definition = definition(pluginId);
        List<PluginDependencyDeclaration> declarations = declarationParser.parseCoordinates(request == null ? null : request.dependencies());
        List<ResolvedPluginDependency> existing = parseResolvedDependencies(definition.dependencyJarsJson());
        List<ResolvedPluginDependency> stored = existing.stream()
                .filter(dependency -> declarations.stream().noneMatch(declaration -> matches(dependency, declaration)))
                .toList();
        registry.updateDependencies(pluginId, stored);
        auditStore.insert("", pluginId, "", actor, "PLUGIN_DEPENDENCY_UNINSTALL", auditDetail(declarations.size(), 0, stored.size(), "remove"));
        return response(pluginId, declarations.size(), stored);
    }

    private PluginDefinition definition(String pluginId) {
        return registry.find(pluginId).orElseThrow(() -> new IllegalArgumentException("Plugin definition not found"));
    }

    private String mode(String value) {
        if (value == null || value.isBlank()) {
            return "merge";
        }
        String mode = value.trim().toLowerCase();
        if (!mode.equals("merge") && !mode.equals("replace")) {
            throw new IllegalArgumentException("Plugin dependency install mode must be merge or replace");
        }
        return mode;
    }

    private List<ResolvedPluginDependency> merge(List<ResolvedPluginDependency> existing, List<ResolvedPluginDependency> resolved) {
        Map<String, ResolvedPluginDependency> dependencies = new LinkedHashMap<>();
        for (ResolvedPluginDependency dependency : existing) {
            dependencies.put(key(dependency), dependency);
        }
        for (ResolvedPluginDependency dependency : resolved) {
            dependencies.put(key(dependency), dependency);
        }
        return List.copyOf(dependencies.values());
    }

    private String key(ResolvedPluginDependency dependency) {
        return dependency.groupId() + ":" + dependency.artifactId() + ":" + dependency.classifier() + ":" + dependency.extension() + ":" + dependency.version();
    }

    private boolean matches(ResolvedPluginDependency dependency, PluginDependencyDeclaration declaration) {
        return dependency.groupId().equals(declaration.groupId())
                && dependency.artifactId().equals(declaration.artifactId())
                && dependency.version().equals(declaration.version());
    }

    private List<PluginDependencyResponse> parseStoredDependencies(String dependencyJarsJson) {
        return parseResolvedDependencies(dependencyJarsJson).stream().map(this::dependencyResponse).toList();
    }

    private List<ResolvedPluginDependency> parseResolvedDependencies(String dependencyJarsJson) {
        try {
            JsonNode root = objectMapper.readTree(dependencyJarsJson == null || dependencyJarsJson.isBlank() ? "[]" : dependencyJarsJson);
            if (!root.isArray()) {
                throw new IllegalArgumentException("Plugin dependency metadata must be an array");
            }
            List<ResolvedPluginDependency> dependencies = new ArrayList<>();
            for (JsonNode dependency : root) {
                dependencies.add(new ResolvedPluginDependency(
                        dependency.path("groupId").asText(""),
                        dependency.path("artifactId").asText(""),
                        dependency.path("version").asText(""),
                        dependency.path("classifier").asText(""),
                        dependency.path("extension").asText(""),
                        dependency.path("relativePath").asText(""),
                        dependency.path("sha256").asText("")
                ));
            }
            return List.copyOf(dependencies);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid plugin dependency metadata", exception);
        }
    }

    private PluginDependencyInstallResponse response(String pluginId, int declaredDependencyCount, List<ResolvedPluginDependency> dependencies) {
        return new PluginDependencyInstallResponse(pluginId, declaredDependencyCount, dependencies.size(), dependencies.stream().map(this::dependencyResponse).toList());
    }

    private PluginDependencyResponse dependencyResponse(ResolvedPluginDependency dependency) {
        return new PluginDependencyResponse(
                dependency.groupId(),
                dependency.artifactId(),
                dependency.version(),
                dependency.classifier(),
                dependency.extension(),
                dependency.relativePath(),
                dependency.sha256()
        );
    }

    private String auditDetail(int declaredDependencyCount, int resolvedDependencyCount, int storedDependencyCount, String mode) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "declaredDependencies", declaredDependencyCount,
                    "resolvedDependencies", resolvedDependencyCount,
                    "storedDependencies", storedDependencyCount,
                    "mode", mode
            ));
        } catch (Exception exception) {
            return "{}";
        }
    }
}
