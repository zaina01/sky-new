package com.sky.job.plugin.script;
import com.sky.job.plugin.artifact.PluginUploadManifestValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
class StandaloneScriptTest {
    @TempDir Path dir;
    @Test void acceptsRawUtf8WithoutExecutingAndGeneratesMetadata()throws Exception {
        for(String extension:new String[]{".py",".js",".mjs"}) {
            Path path=dir.resolve("upload"+extension);Files.writeString(path,"\ufeffnot valid syntax 中文");
            var manifest=StandaloneScript.inspect(path,"demo"+extension,null,null,null);
            new PluginUploadManifestValidator().validate(manifest);
            assertThat(manifest.runtime().executionMode()).isEqualTo("standalone");
            assertThat(manifest.runtime().entryPoint()).isEqualTo("main"+extension);
            assertThat(manifest.pluginId()).startsWith("script-");
            assertThat(manifest.capabilities().account()).isFalse();
        }
    }
    @Test void rejectsInvalidEncodingAndPreservesExplicitIdentity()throws Exception {
        Path path=dir.resolve("upload");Files.write(path,new byte[]{(byte)0xff});
        assertThatThrownBy(()->StandaloneScript.inspect(path,"x.py",null,null,null)).hasMessageContaining("UTF-8");
        Files.writeString(path,"print('ok')");
        var manifest=StandaloneScript.inspect(path,"x.py","existing","Display","v2");
        assertThat(manifest.pluginId()).isEqualTo("existing");assertThat(manifest.version()).isEqualTo("v2");
    }
}
