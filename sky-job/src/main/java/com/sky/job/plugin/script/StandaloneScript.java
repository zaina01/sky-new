package com.sky.job.plugin.script;

import com.sky.job.plugin.artifact.PluginUploadManifest;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Raw script metadata is supplied by the uploader, never evaluated from code. */
public final class StandaloneScript {
    private StandaloneScript() { }
    public static String extension(String filename) {
        String name=filename==null?"":filename.toLowerCase(Locale.ROOT);
        return List.of(".mjs", ".js", ".py").stream().filter(name::endsWith).findFirst().orElse("");
    }
    public static PluginUploadManifest inspect(Path path,String filename,String id,String name,String version) throws IOException {
        if(Files.size(path)>16*1024*1024)throw new IllegalArgumentException("Single script exceeds 16 MiB");
        try {StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(Files.readAllBytes(path)));}
        catch(java.nio.charset.CharacterCodingException e){throw new IllegalArgumentException("Script must use UTF-8 encoding");}
        String ext=extension(filename);
        if(ext.isEmpty())throw new IllegalArgumentException("Unsupported script extension");
        String display=filename.replace('\\','/');display=display.substring(display.lastIndexOf('/')+1);
        return new PluginUploadManifest(blank(id)?"script-"+UUID.randomUUID():id,
                blank(version)?UUID.randomUUID().toString():version,blank(name)?display:name,
                new PluginUploadManifest.RuntimeDescriptor(ext.equals(".py")?"python":"node","","main"+ext,"standalone"),
                new PluginUploadManifest.CapabilityDescriptor(true,false,false,false,false),null,null);
    }
    private static boolean blank(String value){return value==null||value.isBlank();}
}
