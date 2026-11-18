package com.sky.job.plugin.script;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.plugin.artifact.PluginUploadManifestValidator;
import org.apache.commons.compress.archivers.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class ScriptArchiveTest {
    @TempDir Path temporary;
    private final ScriptArchive archive=new ScriptArchive(new ObjectMapper());
    private Path zip(String extra,int mode,String entry) throws Exception {
        Path file=temporary.resolve(java.util.UUID.randomUUID()+".zip");
        try(var out=new ZipArchiveOutputStream(file)) {
            write(out,"sky-script.json","""
                    {"formatVersion":1,"pluginId":"test","version":"1","runtime":{"engine":"node","entry":"%s"},"capabilities":{"task":true}}
                    """.formatted(entry),0);
            write(out,"main.mjs","throw Error('upload must not execute')",0);
            if(extra!=null)write(out,extra,"x",mode);
        }return file;
    }
    private void write(ZipArchiveOutputStream out,String name,String data,int mode)throws Exception {
        var entry=new ZipArchiveEntry(name);if(mode!=0)entry.setUnixMode(mode);out.putArchiveEntry(entry);out.write(data.getBytes(StandardCharsets.UTF_8));out.closeArchiveEntry();
    }
    @Test void extractsMetadataWithoutExecutingAndRejectsMissingEntry()throws Exception {
        var result=archive.inspect(zip(null,0,"main.mjs"));new PluginUploadManifestValidator().validate(result);
        assertThat(result.runtime().engine()).isEqualTo("node");assertThat(result.runtime().mainClass()).isEmpty();
        assertThatThrownBy(()->archive.inspect(zip(null,0,"missing.mjs"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsUnsafeArchiveEntriesAndSymlinks()throws Exception {
        for(String name:new String[]{"../escape","/absolute","C:/escape","node_modules/x","a/site-packages/x",".venv/x","MAIN.MJS","aux.txt"}) {
            assertThatThrownBy(()->archive.inspect(zip(name,0,"main.mjs"))).as(name).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(()->archive.inspect(zip("link",0120777,"main.mjs"))).isInstanceOf(IllegalArgumentException.class);
        Path dos=temporary.resolve("dos.zip");
        try(var out=new java.util.zip.ZipOutputStream(Files.newOutputStream(dos))) {out.putNextEntry(new java.util.zip.ZipEntry("a\\b"));out.write(1);out.closeEntry();}
        assertThatThrownBy(()->archive.inspect(dos)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsafe");
    }
    @Test void rejectsOversizedInflatedEntry()throws Exception {
        Path file=zip(null,0,"main.mjs");
        try(var out=new ZipArchiveOutputStream(file)){write(out,"large", "x".repeat(16*1024*1024+1),0);}
        assertThatThrownBy(()->archive.inspect(file)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("size");
    }
}
