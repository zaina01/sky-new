package com.sky.job.plugin.artifact.dependency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.plugin.artifact.Sha256Digests;
import com.sky.job.plugin.artifact.dependency.dto.LocalPluginDependencyArtifactResponse;
import com.sky.job.plugin.artifact.dependency.dto.LocalPluginDependencyUploadResponse;
import com.sky.job.plugin.storage.PluginAuditStore;
import com.sky.job.scheduler.dto.PageResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

@Service
@EnableConfigurationProperties(PluginDependencyResolutionProperties.class)
public class LocalPluginDependencyArtifactService {
    private final PluginDependencyDeclarationParser declarationParser;
    private final PluginDependencyResolutionProperties properties;
    private final LocalPluginDependencyArtifactStore store;
    private final PluginAuditStore auditStore;
    private final ObjectMapper objectMapper;

    public LocalPluginDependencyArtifactService(
            PluginDependencyDeclarationParser declarationParser,
            PluginDependencyResolutionProperties properties,
            LocalPluginDependencyArtifactStore store,
            PluginAuditStore auditStore,
            ObjectMapper objectMapper
    ) {
        this.declarationParser = declarationParser;
        this.properties = properties;
        this.store = store;
        this.auditStore = auditStore;
        this.objectMapper = objectMapper;
    }

    public PageResponse<LocalPluginDependencyArtifactResponse> list(String keyword, Integer page, Integer size) {
        int normalizedPage = Math.max(page == null ? 1 : page, 1);
        int normalizedSize = Math.min(Math.max(size == null ? 20 : size, 1), 100);
        return new PageResponse<>(
                store.list(keyword, normalizedSize, (normalizedPage - 1) * normalizedSize).stream().map(LocalPluginDependencyArtifactResponse::from).toList(),
                normalizedPage,
                normalizedSize,
                store.count(keyword)
        );
    }

    public LocalPluginDependencyUploadResponse upload(MultipartFile file, String groupId, String artifactId, String version, String actor) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Local dependency jar is required");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!filename.endsWith(".jar")) {
            throw new IllegalArgumentException("Local dependency file must be a jar");
        }
        PluginDependencyDeclaration declaration = declarationParser.parseCoordinates(List.of(groupId + ":" + artifactId + ":" + version)).getFirst();
        Path repositoryRoot = properties.repositoryRoot().toAbsolutePath().normalize();
        Path artifactDirectory = repositoryRoot.resolve(declaration.groupId().replace('.', '/')).resolve(declaration.artifactId()).resolve(declaration.version()).normalize();
        if (!artifactDirectory.startsWith(repositoryRoot)) {
            throw new IllegalArgumentException("Local dependency path escapes Maven repository root");
        }
        String baseName = declaration.artifactId() + "-" + declaration.version();
        Path jarPath = artifactDirectory.resolve(baseName + ".jar").normalize();
        if (!jarPath.startsWith(repositoryRoot)) {
            throw new IllegalArgumentException("Local dependency path escapes Maven repository root");
        }
        try {
            boolean overwritten = Files.exists(jarPath);
            Files.createDirectories(artifactDirectory);
            try (var inputStream = file.getInputStream()) {
                Files.copy(inputStream, jarPath, StandardCopyOption.REPLACE_EXISTING);
            }
            writePom(artifactDirectory.resolve(baseName + ".pom"), declaration);
            String relativePath = repositoryRoot.relativize(jarPath).toString().replace(java.io.File.separatorChar, '/');
            String sha256 = Sha256Digests.digestHex(jarPath);
            long sizeBytes = Files.size(jarPath);
            LocalPluginDependencyArtifact artifact = store.upsert(declaration, relativePath, sha256, filename, sizeBytes, actor);
            auditUpload(artifact, overwritten);
            return LocalPluginDependencyUploadResponse.from(artifact);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store local plugin dependency jar", exception);
        }
    }

    private void auditUpload(LocalPluginDependencyArtifact artifact, boolean overwritten) {
        try {
            auditStore.insert("", "", artifact.uploadedBy(), "PLUGIN_DEPENDENCY_LOCAL_ARTIFACT_UPLOAD", objectMapper.writeValueAsString(Map.of(
                    "groupId", artifact.groupId(),
                    "artifactId", artifact.artifactId(),
                    "version", artifact.version(),
                    "classifier", artifact.classifier(),
                    "extension", artifact.extension(),
                    "relativePath", artifact.relativePath(),
                    "sha256", artifact.sha256(),
                    "originalFilename", artifact.originalFilename(),
                    "sizeBytes", artifact.sizeBytes(),
                    "overwritten", overwritten
            )));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to write local plugin dependency artifact audit", exception);
        }
    }

    private void writePom(Path pomPath, PluginDependencyDeclaration declaration) throws IOException {
        String content = """
                <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                <project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                  <packaging>jar</packaging>
                </project>
                """.formatted(declaration.groupId(), declaration.artifactId(), declaration.version());
        Files.writeString(pomPath, content, StandardCharsets.UTF_8);
    }
}
