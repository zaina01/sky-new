package com.sky.job.plugin.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Component
public class PluginArtifactMetadataExtractor {
    static final String TASK_SPI_ENTRY = "META-INF/services/com.sky.plugin.api.TaskPlugin";
    static final String SERVICE_SPI_ENTRY = "META-INF/services/com.sky.plugin.api.ServicePlugin";
    static final String LEGACY_MANIFEST_ENTRY = "META-INF/sky-plugin.json";
    private static final int MAX_OBJECT_LIST_NESTING_DEPTH = 16;
    private static final List<String> FORBIDDEN_PLATFORM_CLASS_PREFIXES = List.of(
            "com/sky/plugin/api/",
            "com/sky/plugin/protocol/",
            "com/sky/plugin/runner/",
            "io/grpc/"
    );

    private final ObjectMapper objectMapper;
    private final PluginClassMetadataScanner classMetadataScanner = new PluginClassMetadataScanner();

    public PluginArtifactMetadataExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PluginUploadManifest extract(Path artifact) {
        try (JarFile jarFile = new JarFile(artifact.toFile())) {
            if (jarFile.getEntry(LEGACY_MANIFEST_ENTRY) != null) {
                throw new IllegalArgumentException("Legacy META-INF/sky-plugin.json is not supported");
            }
            rejectEmbeddedPlatformClasses(jarFile);
            Map<String, PluginClassMetadataScanner.PluginClassMetadata> classes = classMetadataScanner.scan(jarFile);
            JarEntry taskSpiEntry = jarFile.getJarEntry(TASK_SPI_ENTRY);
            JarEntry serviceSpiEntry = jarFile.getJarEntry(SERVICE_SPI_ENTRY);
            if (taskSpiEntry != null || serviceSpiEntry != null) {
                return extractFromSpi(jarFile, taskSpiEntry, serviceSpiEntry, classes);
            }
            return extractFromAnnotationScan(classes);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect plugin artifact", exception);
        }
    }

    private void rejectEmbeddedPlatformClasses(JarFile jarFile) {
        jarFile.stream()
                .filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".class"))
                .map(JarEntry::getName)
                .filter(name -> FORBIDDEN_PLATFORM_CLASS_PREFIXES.stream().anyMatch(name::startsWith))
                .findFirst()
                .ifPresent(name -> {
                    throw new IllegalArgumentException("Plugin artifact must not embed platform classes: " + name);
                });
    }

    private PluginUploadManifest extractFromSpi(
            JarFile jarFile,
            JarEntry taskSpiEntry,
            JarEntry serviceSpiEntry,
            Map<String, PluginClassMetadataScanner.PluginClassMetadata> classes
    ) throws IOException {
        List<String> providers = new ArrayList<>();
        if (taskSpiEntry != null) {
            providers.addAll(readProviders(jarFile, taskSpiEntry));
        }
        if (serviceSpiEntry != null) {
            providers.addAll(readProviders(jarFile, serviceSpiEntry));
        }
        List<String> distinctProviders = providers.stream().distinct().toList();
        if (distinctProviders.isEmpty()) {
            throw new IllegalArgumentException("Plugin SPI file is empty");
        }
        if (distinctProviders.size() > 1) {
            throw new IllegalArgumentException("Plugin artifact must declare exactly one plugin provider");
        }
        String provider = distinctProviders.getFirst();
        PluginClassMetadataScanner.PluginClassMetadata metadata = classes.get(provider);
        if (metadata == null) {
            throw new IllegalArgumentException("Plugin SPI provider class not found in artifact: " + provider);
        }
        return metadata(metadata, classes, true);
    }

    private List<String> readProviders(JarFile jarFile, JarEntry spiEntry) throws IOException {
        List<String> providers = new ArrayList<>();
        try (InputStream inputStream = jarFile.getInputStream(spiEntry);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String value = line;
                int commentIndex = value.indexOf('#');
                if (commentIndex >= 0) {
                    value = value.substring(0, commentIndex);
                }
                value = value.trim();
                if (!value.isEmpty()) {
                    providers.add(value);
                }
            }
        }
        return providers;
    }

    private PluginUploadManifest extractFromAnnotationScan(Map<String, PluginClassMetadataScanner.PluginClassMetadata> classes) {
        List<PluginClassMetadataScanner.PluginClassMetadata> annotatedClasses = classes.values().stream()
                .filter(metadata -> metadata.skyPlugin() != null)
                .toList();
        if (annotatedClasses.isEmpty()) {
            throw new IllegalArgumentException("Plugin artifact must declare TaskPlugin/ServicePlugin SPI or exactly one @SkyPlugin class");
        }
        if (annotatedClasses.size() > 1) {
            throw new IllegalArgumentException("Plugin artifact contains multiple @SkyPlugin classes");
        }
        return metadata(annotatedClasses.getFirst(), classes, false);
    }

    private PluginUploadManifest metadata(
            PluginClassMetadataScanner.PluginClassMetadata pluginClass,
            Map<String, PluginClassMetadataScanner.PluginClassMetadata> classes,
            boolean fromSpi
    ) {
        if (!pluginClass.directlyImplementsTaskPlugin() && !pluginClass.directlyImplementsServicePlugin()) {
            throw new IllegalArgumentException("Plugin class must directly implement TaskPlugin or ServicePlugin: " + pluginClass.className());
        }
        PluginClassMetadataScanner.SkyPluginMetadata annotation = pluginClass.skyPlugin();
        if (annotation == null) {
            String source = fromSpi ? "SPI provider" : "annotated plugin class";
            throw new IllegalArgumentException(source + " is missing @SkyPlugin: " + pluginClass.className());
        }
        return new PluginUploadManifest(
                annotation.pluginId(),
                annotation.version(),
                annotation.displayName(),
                new PluginUploadManifest.RuntimeDescriptor(runtimeValue(annotation.runtime()), pluginClass.className()),
                capabilities(annotation.capabilities()),
                configSchema(annotation.configClass(), classes),
                accountSchema(annotation.accountClass(), classes)
        );
    }

    private PluginUploadManifest.CapabilityDescriptor capabilities(List<String> capabilities) {
        Set<String> capabilitySet = Set.copyOf(capabilities);
        return new PluginUploadManifest.CapabilityDescriptor(
                capabilitySet.contains("TASK"),
                capabilitySet.contains("CONFIG"),
                capabilitySet.contains("ACCOUNT"),
                capabilitySet.contains("NOTIFICATION"),
                capabilitySet.contains("SERVICE")
        );
    }

    private String runtimeValue(String runtime) {
        return switch (runtime) {
            case "JAVA_PROCESS" -> "java-process";
            default -> throw new IllegalArgumentException("Unsupported plugin runtime: " + runtime);
        };
    }

    private JsonNode configSchema(String schemaClass, Map<String, PluginClassMetadataScanner.PluginClassMetadata> classes) {
        if (PluginClassMetadataScanner.VOID_CLASS_NAME.equals(schemaClass)) {
            return objectMapper.createObjectNode();
        }
        PluginClassMetadataScanner.PluginClassMetadata metadata = classes.get(schemaClass);
        if (metadata == null) {
            throw new IllegalArgumentException("Plugin config class not found in artifact: " + schemaClass);
        }
        if (metadata.pluginConfig() == null) {
            throw new IllegalArgumentException("Plugin config class is missing @PluginConfig: " + schemaClass);
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("fields", configFields(metadata, classes, ObjectListTraversal.root(metadata.className())));
        return objectMapper.valueToTree(schema);
    }

    private List<Map<String, Object>> configFields(
            PluginClassMetadataScanner.PluginClassMetadata metadata,
            Map<String, PluginClassMetadataScanner.PluginClassMetadata> classes,
            ObjectListTraversal traversal
    ) {
        List<PluginClassMetadataScanner.FieldMetadata> configFields = new ArrayList<>();
        Map<String, PluginClassMetadataScanner.ConfigFieldMetadata> annotationsByName = new LinkedHashMap<>();
        for (PluginClassMetadataScanner.FieldMetadata field : metadata.fields()) {
            PluginClassMetadataScanner.ConfigFieldMetadata annotation = field.configField();
            if (annotation == null) {
                continue;
            }
            String name = field.name();
            if (annotationsByName.put(name, annotation) != null) {
                throw new IllegalArgumentException("Duplicate plugin config field: " + name);
            }
            configFields.add(field);
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        for (PluginClassMetadataScanner.FieldMetadata field : configFields) {
            fields.add(configField(field, classes, annotationsByName, traversal));
        }
        return fields;
    }

    private Map<String, Object> configField(
            PluginClassMetadataScanner.FieldMetadata field,
            Map<String, PluginClassMetadataScanner.PluginClassMetadata> classes,
            Map<String, PluginClassMetadataScanner.ConfigFieldMetadata> annotationsByName,
            ObjectListTraversal traversal
    ) {
        PluginClassMetadataScanner.ConfigFieldMetadata annotation = field.configField();
        String name = field.name();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", name);
        value.put("label", annotation.label());
        value.put("type", annotation.type());
        value.put("required", annotation.required());
        value.put("options", annotation.options());
        Map<String, Object> visibleWhen = visibleWhen(name, annotation, annotationsByName);
        if (!visibleWhen.isEmpty()) {
            value.put("visibleWhen", visibleWhen);
        }
        if ("OBJECT_LIST".equals(annotation.type())) {
            value.put("itemSchema", objectListItemSchema(name, annotation, classes, traversal));
        } else if (!PluginClassMetadataScanner.VOID_CLASS_NAME.equals(annotation.itemClass())) {
            throw new IllegalArgumentException("Plugin config itemClass can only be used with OBJECT_LIST: " + name);
        }
        return value;
    }

    private Map<String, Object> objectListItemSchema(
            String fieldName,
            PluginClassMetadataScanner.ConfigFieldMetadata annotation,
            Map<String, PluginClassMetadataScanner.PluginClassMetadata> classes,
            ObjectListTraversal traversal
    ) {
        if (!annotation.options().isEmpty()) {
            throw new IllegalArgumentException("Plugin OBJECT_LIST config field must not declare options: " + fieldName);
        }
        if (PluginClassMetadataScanner.VOID_CLASS_NAME.equals(annotation.itemClass())) {
            throw new IllegalArgumentException("Plugin OBJECT_LIST config field must declare itemClass: " + fieldName);
        }
        PluginClassMetadataScanner.PluginClassMetadata itemMetadata = classes.get(annotation.itemClass());
        if (itemMetadata == null) {
            throw new IllegalArgumentException("Plugin OBJECT_LIST item class not found in artifact: " + annotation.itemClass());
        }
        ObjectListTraversal itemTraversal = traversal.enter("config", fieldName, annotation.itemClass());
        List<Map<String, Object>> itemFields = configFields(itemMetadata, classes, itemTraversal);
        if (itemFields.isEmpty()) {
            throw new IllegalArgumentException("Plugin OBJECT_LIST item class must declare config fields: " + annotation.itemClass());
        }
        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("fields", itemFields);
        return itemSchema;
    }

    private Map<String, Object> visibleWhen(String fieldName, PluginClassMetadataScanner.ConfigFieldMetadata annotation, Map<String, PluginClassMetadataScanner.ConfigFieldMetadata> annotationsByName) {
        String controlField = trim(annotation.visibleWhenField());
        String equals = trim(annotation.visibleWhenEquals());
        List<String> in = annotation.visibleWhenIn().stream().map(this::trim).filter(value -> value != null).toList();
        if (controlField == null && equals == null && in.isEmpty()) {
            return Map.of();
        }
        if (controlField == null) {
            throw new IllegalArgumentException("Plugin config visibleWhen field is required: " + fieldName);
        }
        if (equals != null && !in.isEmpty()) {
            throw new IllegalArgumentException("Plugin config visibleWhen must use either equals or in: " + fieldName);
        }
        if (equals == null && in.isEmpty()) {
            throw new IllegalArgumentException("Plugin config visibleWhen condition is required: " + fieldName);
        }
        PluginClassMetadataScanner.ConfigFieldMetadata controlAnnotation = annotationsByName.get(controlField);
        if (controlAnnotation == null) {
            throw new IllegalArgumentException("Plugin config visibleWhen references unknown field: " + controlField);
        }
        if (fieldName.equals(controlField)) {
            throw new IllegalArgumentException("Plugin config field cannot depend on itself: " + fieldName);
        }
        Map<String, Object> visibleWhen = new LinkedHashMap<>();
        visibleWhen.put("field", controlField);
        if (equals != null) {
            visibleWhen.put("equals", visibleWhenValue(fieldName, controlAnnotation, equals));
        } else {
            visibleWhen.put("in", in.stream().map(value -> visibleWhenValue(fieldName, controlAnnotation, value)).toList());
        }
        return visibleWhen;
    }

    private Object visibleWhenValue(String fieldName, PluginClassMetadataScanner.ConfigFieldMetadata controlAnnotation, String value) {
        if ("SWITCH".equals(controlAnnotation.type())) {
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
            throw new IllegalArgumentException("Plugin config SWITCH visibleWhen value must be true or false: " + fieldName);
        }
        return value;
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private JsonNode accountSchema(String schemaClass, Map<String, PluginClassMetadataScanner.PluginClassMetadata> classes) {
        if (PluginClassMetadataScanner.VOID_CLASS_NAME.equals(schemaClass)) {
            return objectMapper.createObjectNode();
        }
        PluginClassMetadataScanner.PluginClassMetadata metadata = classes.get(schemaClass);
        if (metadata == null) {
            throw new IllegalArgumentException("Plugin account class not found in artifact: " + schemaClass);
        }
        if (metadata.pluginAccount() == null) {
            throw new IllegalArgumentException("Plugin account class is missing @PluginAccount: " + schemaClass);
        }
        AccountFields accountFields = accountFields(
                metadata,
                classes,
                ObjectListTraversal.root(metadata.className()),
                true
        );
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("fields", accountFields.fields());
        if (accountFields.accountIdField() != null) {
            schema.put("accountIdField", accountFields.accountIdField());
        }
        return objectMapper.valueToTree(schema);
    }

    private AccountFields accountFields(
            PluginClassMetadataScanner.PluginClassMetadata metadata,
            Map<String, PluginClassMetadataScanner.PluginClassMetadata> classes,
            ObjectListTraversal traversal,
            boolean allowAccountId
    ) {
        List<PluginClassMetadataScanner.FieldMetadata> accountFields = new ArrayList<>();
        Map<String, PluginClassMetadataScanner.AccountFieldMetadata> annotationsByName = new LinkedHashMap<>();
        String accountIdField = null;
        for (PluginClassMetadataScanner.FieldMetadata field : metadata.fields()) {
            PluginClassMetadataScanner.AccountFieldMetadata annotation = field.accountField();
            boolean accountId = field.accountId();
            if (annotation == null && !accountId) {
                continue;
            }
            if (accountId && !allowAccountId) {
                throw new IllegalArgumentException("Plugin OBJECT_LIST account item field cannot declare @AccountId: " + field.name());
            }
            if (annotation == null) {
                throw new IllegalArgumentException("@AccountId field must also declare @AccountField: " + field.name());
            }
            if (accountId) {
                if (accountIdField != null) {
                    throw new IllegalArgumentException("Plugin account class must declare at most one @AccountId field");
                }
                if (annotation.sensitive()) {
                    throw new IllegalArgumentException("@AccountId field cannot be sensitive: " + field.name());
                }
                accountIdField = field.name();
            }
            String name = field.name();
            if (annotationsByName.put(name, annotation) != null) {
                throw new IllegalArgumentException("Duplicate plugin account field: " + name);
            }
            accountFields.add(field);
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        for (PluginClassMetadataScanner.FieldMetadata field : accountFields) {
            PluginClassMetadataScanner.AccountFieldMetadata annotation = field.accountField();
            boolean accountId = field.accountId();
            String name = field.name();
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", name);
            value.put("label", annotation.label());
            value.put("type", annotation.type());
            value.put("required", annotation.required());
            value.put("sensitive", annotation.sensitive());
            value.put("readonly", annotation.readonly());
            value.put("accountId", accountId);
            value.put("options", annotation.options());
            Map<String, Object> visibleWhen = accountVisibleWhen(name, annotation, annotationsByName);
            if (!visibleWhen.isEmpty()) {
                value.put("visibleWhen", visibleWhen);
            }
            if ("OBJECT_LIST".equals(annotation.type())) {
                value.put("itemSchema", accountObjectListItemSchema(name, annotation, classes, traversal));
            } else if (!PluginClassMetadataScanner.VOID_CLASS_NAME.equals(annotation.itemClass())) {
                throw new IllegalArgumentException("Plugin account itemClass can only be used with OBJECT_LIST: " + name);
            }
            fields.add(value);
        }
        return new AccountFields(List.copyOf(fields), accountIdField);
    }

    private Map<String, Object> accountObjectListItemSchema(
            String fieldName,
            PluginClassMetadataScanner.AccountFieldMetadata annotation,
            Map<String, PluginClassMetadataScanner.PluginClassMetadata> classes,
            ObjectListTraversal traversal
    ) {
        if (!annotation.options().isEmpty()) {
            throw new IllegalArgumentException("Plugin OBJECT_LIST account field must not declare options: " + fieldName);
        }
        if (PluginClassMetadataScanner.VOID_CLASS_NAME.equals(annotation.itemClass())) {
            throw new IllegalArgumentException("Plugin OBJECT_LIST account field must declare itemClass: " + fieldName);
        }
        PluginClassMetadataScanner.PluginClassMetadata itemMetadata = classes.get(annotation.itemClass());
        if (itemMetadata == null) {
            throw new IllegalArgumentException("Plugin OBJECT_LIST account item class not found in artifact: " + annotation.itemClass());
        }
        ObjectListTraversal itemTraversal = traversal.enter("account", fieldName, annotation.itemClass());
        AccountFields itemFields = accountFields(itemMetadata, classes, itemTraversal, false);
        if (itemFields.fields().isEmpty()) {
            throw new IllegalArgumentException("Plugin OBJECT_LIST item class must declare account fields: " + annotation.itemClass());
        }
        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("fields", itemFields.fields());
        return itemSchema;
    }

    private Map<String, Object> accountVisibleWhen(
            String fieldName,
            PluginClassMetadataScanner.AccountFieldMetadata annotation,
            Map<String, PluginClassMetadataScanner.AccountFieldMetadata> annotationsByName
    ) {
        String controlField = trim(annotation.visibleWhenField());
        String equals = trim(annotation.visibleWhenEquals());
        List<String> in = annotation.visibleWhenIn().stream().map(this::trim).filter(value -> value != null).toList();
        if (controlField == null && equals == null && in.isEmpty()) {
            return Map.of();
        }
        if (controlField == null) {
            throw new IllegalArgumentException("Plugin account visibleWhen field is required: " + fieldName);
        }
        if (equals != null && !in.isEmpty()) {
            throw new IllegalArgumentException("Plugin account visibleWhen must use either equals or in: " + fieldName);
        }
        if (equals == null && in.isEmpty()) {
            throw new IllegalArgumentException("Plugin account visibleWhen condition is required: " + fieldName);
        }
        PluginClassMetadataScanner.AccountFieldMetadata controlAnnotation = annotationsByName.get(controlField);
        if (controlAnnotation == null) {
            throw new IllegalArgumentException("Plugin account visibleWhen references unknown field: " + controlField);
        }
        if (fieldName.equals(controlField)) {
            throw new IllegalArgumentException("Plugin account field cannot depend on itself: " + fieldName);
        }
        Map<String, Object> visibleWhen = new LinkedHashMap<>();
        visibleWhen.put("field", controlField);
        if (equals != null) {
            visibleWhen.put("equals", accountVisibleWhenValue(fieldName, controlAnnotation, equals));
        } else {
            visibleWhen.put("in", in.stream().map(value -> accountVisibleWhenValue(fieldName, controlAnnotation, value)).toList());
        }
        return visibleWhen;
    }

    private Object accountVisibleWhenValue(
            String fieldName,
            PluginClassMetadataScanner.AccountFieldMetadata controlAnnotation,
            String value
    ) {
        if ("SWITCH".equals(controlAnnotation.type())) {
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
            throw new IllegalArgumentException("Plugin account SWITCH visibleWhen value must be true or false: " + fieldName);
        }
        return value;
    }

    private record ObjectListTraversal(int depth, List<String> classPath) {
        private static ObjectListTraversal root(String className) {
            return new ObjectListTraversal(0, List.of(className));
        }

        private ObjectListTraversal enter(String schemaKind, String fieldName, String itemClass) {
            List<String> nextClassPath = new ArrayList<>(classPath);
            nextClassPath.add(itemClass);
            String formattedPath = String.join(" -> ", nextClassPath);
            if (classPath.contains(itemClass)) {
                throw new IllegalArgumentException(
                        "Plugin OBJECT_LIST " + schemaKind + " itemClass cycle detected at field " + fieldName + ": " + formattedPath
                );
            }
            if (depth >= MAX_OBJECT_LIST_NESTING_DEPTH) {
                throw new IllegalArgumentException(
                        "Plugin OBJECT_LIST " + schemaKind + " nesting exceeds maximum depth "
                                + MAX_OBJECT_LIST_NESTING_DEPTH + " at field " + fieldName + ": " + formattedPath
                );
            }
            return new ObjectListTraversal(depth + 1, List.copyOf(nextClassPath));
        }
    }

    private record AccountFields(List<Map<String, Object>> fields, String accountIdField) {
    }
}
