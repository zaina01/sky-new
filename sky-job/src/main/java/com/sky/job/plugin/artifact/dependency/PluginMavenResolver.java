package com.sky.job.plugin.artifact.dependency;

import org.apache.maven.repository.internal.ArtifactDescriptorUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.util.graph.manager.ClassicDependencyManager;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
import org.eclipse.aether.util.graph.transformer.ChainedDependencyGraphTransformer;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.transformer.JavaDependencyContextRefiner;
import org.eclipse.aether.util.graph.transformer.JavaScopeDeriver;
import org.eclipse.aether.util.graph.transformer.JavaScopeSelector;
import org.eclipse.aether.util.graph.transformer.NearestVersionSelector;
import org.eclipse.aether.util.graph.transformer.SimpleOptionalitySelector;
import org.eclipse.aether.util.graph.traverser.FatArtifactTraverser;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Component
public class PluginMavenResolver {
    private final RepositorySystem repositorySystem;
    private final PluginDependencyResolutionProperties properties;

    public PluginMavenResolver(PluginDependencyResolutionProperties properties) {
        this.repositorySystem = new RepositorySystemSupplier().get();
        this.properties = properties;
    }

    public List<Artifact> resolveRuntimeArtifacts(String coordinate) {
        try {
            Files.createDirectories(properties.repositoryRoot());
            try (RepositorySystemSession.CloseableSession session = session()) {
                Dependency dependency = new Dependency(new org.eclipse.aether.artifact.DefaultArtifact(coordinate), "runtime");
                CollectRequest collectRequest = new CollectRequest();
                collectRequest.setRoot(dependency);
                repositories().forEach(collectRequest::addRepository);
                DependencyNode node = repositorySystem.collectDependencies(session, collectRequest).getRoot();
                DependencyRequest dependencyRequest = new DependencyRequest();
                dependencyRequest.setRoot(node);
                repositorySystem.resolveDependencies(session, dependencyRequest);
                PreorderNodeListGenerator generator = new PreorderNodeListGenerator();
                node.accept(generator);
                return List.copyOf(generator.getArtifacts(false));
            }
        } catch (DependencyCollectionException | DependencyResolutionException exception) {
            throw new IllegalArgumentException("Failed to resolve plugin dependency: " + coordinate, exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to prepare plugin dependency resolver", exception);
        }
    }

    private RepositorySystemSession.CloseableSession session() {
        RepositorySystemSession.SessionBuilder builder = repositorySystem.createSessionBuilder();
        builder.setDependencyTraverser(new FatArtifactTraverser());
        builder.setDependencyManager(new ClassicDependencyManager());
        DependencySelector selector = new AndDependencySelector(new ScopeDependencySelector("test", "provided"), new OptionalDependencySelector(), new ExclusionDependencySelector());
        builder.setDependencySelector(selector);
        DependencyGraphTransformer transformer = new ConflictResolver(new NearestVersionSelector(), new JavaScopeSelector(), new SimpleOptionalitySelector(), new JavaScopeDeriver());
        transformer = new ChainedDependencyGraphTransformer(transformer, new JavaDependencyContextRefiner());
        builder.setDependencyGraphTransformer(transformer);
        builder.setArtifactTypeRegistry(MavenRepositorySystemUtils.newArtifactTypeRegistry());
        builder.setArtifactDescriptorPolicy(new SimpleArtifactDescriptorPolicy(true, true));
        builder.withLocalRepositories(new LocalRepository(properties.repositoryRoot().toAbsolutePath().normalize().toString()));
        builder.setSystemProperties(System.getProperties());
        return builder.build();
    }

    private List<org.eclipse.aether.repository.RemoteRepository> repositories() {
        List<org.eclipse.aether.repository.RemoteRepository> repositories = new ArrayList<>();
        for (int index = 0; index < properties.remoteRepositories().size(); index++) {
            org.apache.maven.model.Repository repository = new org.apache.maven.model.Repository();
            repository.setId("plugin-repository-" + index);
            repository.setLayout("default");
            repository.setUrl(properties.remoteRepositories().get(index));
            repositories.add(ArtifactDescriptorUtils.toRemoteRepository(repository));
        }
        return List.copyOf(repositories);
    }
}
