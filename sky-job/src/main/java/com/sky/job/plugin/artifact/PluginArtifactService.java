package com.sky.job.plugin.artifact;

import com.sky.job.plugin.artifact.dependency.PluginDependencyDeclaration;
import com.sky.job.plugin.artifact.dependency.PluginDependencyDeclarationParser;
import com.sky.job.plugin.artifact.dependency.PluginMavenDependencyResolver;
import com.sky.job.plugin.artifact.dependency.ResolvedPluginDependency;
import com.sky.job.plugin.security.PluginCapability;
import com.sky.job.plugin.storage.PluginAuditStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@Service
@EnableConfigurationProperties(PluginArtifactProperties.class)
public class PluginArtifactService {
    private final PluginArtifactStore artifactStore;
    private final PluginUploadManifestValidator manifestValidator;
    private final PluginArtifactRegistry registry;
    private final PluginDependencyDeclarationParser dependencyParser;
    private final PluginMavenDependencyResolver dependencyResolver;
    private final PluginAuditStore auditStore;

    public PluginArtifactService(
            PluginArtifactStore artifactStore,
            PluginUploadManifestValidator manifestValidator,
            PluginArtifactRegistry registry,
            PluginDependencyDeclarationParser dependencyParser,
            PluginMavenDependencyResolver dependencyResolver,
            PluginAuditStore auditStore
    ) {
        this.artifactStore = artifactStore;
        this.manifestValidator = manifestValidator;
        this.registry = registry;
        this.dependencyParser = dependencyParser;
        this.dependencyResolver = dependencyResolver;
        this.auditStore = auditStore;
    }

    public synchronized PluginUploadResult upload(MultipartFile file, String actor) {
        return upload(file,actor,null,null,null);
    }

    public synchronized PluginUploadResult upload(MultipartFile file,String actor,String pluginId,String displayName,String version) {
        PluginArtifactStore.StoredArtifact storedArtifact = artifactStore.store(file,pluginId,displayName,version);
        PluginUploadManifest manifest = storedArtifact.manifest();
        Set<PluginCapability> capabilities = manifestValidator.validate(manifest);
        registry.find(manifest.pluginId()).ifPresent(existing -> {
            if (!existing.runtimeEngine().equals(manifest.runtime().engine())) throw new IllegalArgumentException("Cannot replace a plugin with a different runtime engine");
            if (!existing.executionMode().equals(manifest.runtime().executionMode())) throw new IllegalArgumentException("Cannot replace a plugin with a different execution mode");
        });
        boolean overwriteDependencies = "java".equals(manifest.runtime().engine()) && dependencyParser.hasDependencyDescriptor(storedArtifact.path());
        List<PluginDependencyDeclaration> declarations = overwriteDependencies ? dependencyParser.parse(storedArtifact.path()) : List.of();
        List<ResolvedPluginDependency> dependencies = overwriteDependencies ? dependencyResolver.resolve(declarations) : List.of();
        registry.register(manifest, capabilities, storedArtifact.path(), storedArtifact.sha256(), dependencies, overwriteDependencies, actor);
        auditStore.insert(manifest.pluginId(), "", actor, "PLUGIN_DEFINITION_UPLOAD", "{\"sha256\":\"" + storedArtifact.sha256() + "\",\"dependencyDescriptor\":" + overwriteDependencies + ",\"declaredDependencies\":" + declarations.size() + ",\"resolvedDependencies\":" + dependencies.size() + "}");
        return new PluginUploadResult(
                manifest.pluginId(),
                manifest.version(),
                manifest.runtime().mainClass(),
                storedArtifact.sha256(),
                storedArtifact.path(),
                dependencies.size(), manifest.runtime().engine(), manifest.runtime().entryPoint(), manifest.runtime().executionMode()
        );
    }
}
