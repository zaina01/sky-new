package com.sky.job.plugin.script;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.plugin.artifact.PluginUploadManifest;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Validates bytes before extracting; upload never imports user code. */
@Component
public class ScriptArchive {
    private final ObjectMapper json;
    public ScriptArchive(ObjectMapper json) { this.json = json; }

    public static void validateEntryPoint(String engine, String entry) {
        safeName(entry);
        if (!entry.endsWith("node".equals(engine) ? ".mjs" : ".py"))
            throw new IllegalArgumentException("Script entry must be .mjs (node) or .py (python)");
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.contains("//") || name.contains("\\") || name.contains(":"))
            throw new IllegalArgumentException("Unsafe script archive path");
        for (String part : name.split("/")) {
            if (part.equals("..") || part.equals(".") || part.endsWith(".") || part.endsWith(" ") || part.indexOf('\0') >= 0)
                throw new IllegalArgumentException("Unsafe script archive path");
            String lower = part.toLowerCase(Locale.ROOT);
            if (Set.of("node_modules", "site-packages", ".venv", "venv").contains(lower) || lower.equals("pyvenv.cfg"))
                throw new IllegalArgumentException("Install third-party dependencies in Script environments");
            if (lower.matches("(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?"))
                throw new IllegalArgumentException("Reserved archive filename");
        }
        return name;
    }

    public PluginUploadManifest inspect(Path archive) throws IOException { return read(archive, null); }
    public void extract(Path archive, Path destination) throws IOException { read(archive, destination.toAbsolutePath().normalize()); }

    private PluginUploadManifest read(Path archive, Path destination) throws IOException {
        Set<String> names = new HashSet<>();
        byte[] manifest = null;
        long total = 0;
        try (ZipFile zip = ZipFile.builder().setPath(archive).get()) {
            var entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                // Commons Compress normalizes DOS backslashes; validate original bytes as well.
                if (entry.getRawName()!=null) safeName(new String(entry.getRawName(),java.nio.charset.StandardCharsets.UTF_8));
                String name = safeName(entry.getName());
                String key = name.replaceAll("/+$", "").toLowerCase(Locale.ROOT);
                int kind=entry.getUnixMode() & 0170000;
                if (!names.add(key) || names.size() > 10000 || entry.isUnixSymlink() || (kind!=0 && kind!=0100000 && kind!=0040000) || !zip.canReadEntryData(entry))
                    throw new IllegalArgumentException("Duplicate, unsupported or excessive script archive entries");
                if (entry.isDirectory()) continue;
                try (var input = zip.getInputStream(entry)) {
                    byte[] data = input.readNBytes(16 * 1024 * 1024 + 1);
                    total += data.length;
                    if (data.length > 16 * 1024 * 1024 || total > 100 * 1024 * 1024)
                        throw new IllegalArgumentException("Script archive exceeds extracted size limit");
                    if (name.equals("sky-script.json")) {
                        if (data.length > 1024 * 1024) throw new IllegalArgumentException("Script manifest too large");
                        manifest = data;
                    }
                    if (destination != null) {
                        Path target = destination.resolve(name).normalize();
                        if (!target.startsWith(destination)) throw new IllegalArgumentException("Archive path escapes destination");
                        Files.createDirectories(target.getParent());
                        Files.write(target, data, StandardOpenOption.CREATE_NEW);
                    }
                }
            }
        }
        if (manifest == null) throw new IllegalArgumentException("Missing sky-script.json");
        var root = json.readTree(manifest);
        if (!root.isObject()) throw new IllegalArgumentException("Script manifest must be an object");
        if (root.path("formatVersion").asInt() != 1) throw new IllegalArgumentException("Unsupported script formatVersion");
        String engine = root.path("runtime").path("engine").asText();
        String entry = root.path("runtime").path("entry").asText();
        if (!Set.of("node", "python").contains(engine)) throw new IllegalArgumentException("Unsupported script engine");
        validateEntryPoint(engine, entry);
        validateSchema(root.get("configSchema"),0);
        validateSchema(root.get("accountSchema"),0);
        var caps=root.get("capabilities");
        if (caps==null || !caps.isObject()) throw new IllegalArgumentException("Missing script capabilities");
        for(String capability:List.of("task","config","account","notification","service"))
            if(caps.has(capability) && !caps.get(capability).isBoolean()) throw new IllegalArgumentException("Capabilities must be booleans");
        // Require the exact spelling, including case, on every supported OS.
        try (ZipFile zip = ZipFile.builder().setPath(archive).get()) {
            if (zip.getEntry(entry) == null || zip.getEntry(entry).isDirectory()) throw new IllegalArgumentException("Script entry not found");
        }
        return new PluginUploadManifest(root.path("pluginId").asText(), root.path("version").asText(), root.path("displayName").asText(),
                new PluginUploadManifest.RuntimeDescriptor(engine, "", entry),
                json.treeToValue(root.get("capabilities"), PluginUploadManifest.CapabilityDescriptor.class),
                root.get("configSchema"), root.get("accountSchema"));
    }

    private void validateSchema(com.fasterxml.jackson.databind.JsonNode schema,int depth) {
        if(schema==null || schema.isNull()) return;
        if(!schema.isObject() || depth>16)throw new IllegalArgumentException("Invalid or excessively nested script schema");
        var fields=schema.get("fields");
        if(fields==null && schema.isEmpty())return;
        if(fields==null || !fields.isArray() || fields.size()>1000)throw new IllegalArgumentException("Schema fields must be an array (max 1000)");
        Set<String> names=new HashSet<>();
        for(var field:fields){
            String name=field.path("name").asText();
            if(!field.isObject() || !name.matches("[A-Za-z_$][A-Za-z0-9_$]*") || !names.add(name))throw new IllegalArgumentException("Invalid or duplicate schema field");
            try {com.sky.plugin.api.ConfigFieldType.valueOf(field.path("type").asText());}
            catch(IllegalArgumentException e){throw new IllegalArgumentException("Unknown script schema field type");}
            for(String flag:List.of("required","sensitive","readonly","accountId"))
                if(field.has(flag) && !field.get(flag).isBoolean())throw new IllegalArgumentException("Schema flags must be booleans");
            if(field.has("options") && !field.get("options").isArray())throw new IllegalArgumentException("Schema options must be an array");
            if(field.has("visibleWhen") && !field.get("visibleWhen").isObject())throw new IllegalArgumentException("visibleWhen must be an object");
            if(field.path("type").asText().equals("OBJECT_LIST") && !field.has("itemSchema"))throw new IllegalArgumentException("OBJECT_LIST requires itemSchema");
            validateSchema(field.get("itemSchema"),depth+1);
        }
        if(schema.has("accountIdField") && !names.contains(schema.path("accountIdField").asText()))throw new IllegalArgumentException("accountIdField must reference a schema field");
    }
}
