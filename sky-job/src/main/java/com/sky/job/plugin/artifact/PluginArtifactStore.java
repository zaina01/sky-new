package com.sky.job.plugin.artifact;

import com.sky.job.system.DynamicPluginArtifactSettings;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

@Component
public class PluginArtifactStore {
    private final PluginArtifactProperties properties;
    private final DynamicPluginArtifactSettings artifactSettings;
    private final PluginArtifactMetadataExtractor metadataExtractor;
    private final com.sky.job.plugin.script.ScriptArchive scripts;

    public PluginArtifactStore(PluginArtifactProperties properties, DynamicPluginArtifactSettings artifactSettings, PluginArtifactMetadataExtractor metadataExtractor, com.sky.job.plugin.script.ScriptArchive scripts) {
        this.properties = properties;
        this.artifactSettings = artifactSettings;
        this.metadataExtractor = metadataExtractor;
        this.scripts = scripts;
    }

    public StoredArtifact store(MultipartFile file) {
        return store(file,null,null,null);
    }

    public StoredArtifact store(MultipartFile file,String pluginId,String displayName,String version) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Plugin artifact is empty");
        }
        if (file.getSize() > artifactSettings.maxArtifactBytes()) {
            throw new IllegalArgumentException("Plugin artifact exceeds max size");
        }
        Path root = properties.artifactRoot().toAbsolutePath().normalize();
        Path temporary = root.resolve(UUID.randomUUID() + ".upload").normalize();
        try {
            Files.createDirectories(root);
            if (!temporary.startsWith(root)) {
                throw new IllegalArgumentException("Plugin artifact path escapes configured root");
            }
            try (InputStream inputStream = new BufferedInputStream(file.getInputStream())) {
                Files.copy(inputStream, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            boolean script = file.getOriginalFilename() != null && file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT).endsWith(".zip");
            String extension=com.sky.job.plugin.script.StandaloneScript.extension(file.getOriginalFilename());
            boolean standalone=!extension.isEmpty();
            if (!script && !standalone) {
                if(file.getOriginalFilename()==null || !file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT).endsWith(".jar"))throw new IllegalArgumentException("Unsupported plugin file extension");
                validateEntries(temporary);
            }
            PluginUploadManifest manifest = standalone ? com.sky.job.plugin.script.StandaloneScript.inspect(temporary,file.getOriginalFilename(),pluginId,displayName,version) : script ? scripts.inspect(temporary) : metadataExtractor.extract(temporary);
            new PluginUploadManifestValidator().validate(manifest);
            String sha256 = Sha256Digests.digestHex(temporary);
            Path finalPath = root.resolve(sha256 + (standalone ? extension : script ? ".zip" : ".jar")).normalize();
            if (!finalPath.startsWith(root)) {
                throw new IllegalArgumentException("Plugin artifact path escapes configured root");
            }
            Files.move(temporary, finalPath, StandardCopyOption.REPLACE_EXISTING);
            return new StoredArtifact(finalPath, sha256, manifest);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to store plugin artifact", exception);
        } finally {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    private void validateEntries(Path artifact) throws IOException {
        try (JarInputStream jarInputStream = new JarInputStream(Files.newInputStream(artifact))) {
            JarEntry entry;
            while ((entry = jarInputStream.getNextJarEntry()) != null) {
                validateEntry(entry);
            }
        }
    }

    private void validateEntry(JarEntry entry) {
        Path normalized = Path.of(entry.getName()).normalize();
        if (entry.getName().startsWith("/") || normalized.startsWith("..") || entry.getName().contains("\\")) {
            throw new IllegalArgumentException("Plugin artifact contains unsafe ZIP entry");
        }
    }

    public record StoredArtifact(Path path, String sha256, PluginUploadManifest manifest) {
    }
}
