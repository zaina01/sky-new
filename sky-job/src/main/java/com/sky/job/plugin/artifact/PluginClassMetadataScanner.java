package com.sky.job.plugin.artifact;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class PluginClassMetadataScanner {
    static final String SKY_PLUGIN_DESCRIPTOR = "Lcom/sky/plugin/api/SkyPlugin;";
    static final String PLUGIN_CONFIG_DESCRIPTOR = "Lcom/sky/plugin/api/PluginConfig;";
    static final String PLUGIN_ACCOUNT_DESCRIPTOR = "Lcom/sky/plugin/api/PluginAccount;";
    static final String CONFIG_FIELD_DESCRIPTOR = "Lcom/sky/plugin/api/ConfigField;";
    static final String ACCOUNT_FIELD_DESCRIPTOR = "Lcom/sky/plugin/api/AccountField;";
    static final String ACCOUNT_ID_DESCRIPTOR = "Lcom/sky/plugin/api/AccountId;";
    static final String TASK_PLUGIN_INTERNAL_NAME = "com/sky/plugin/api/TaskPlugin";
    static final String SERVICE_PLUGIN_INTERNAL_NAME = "com/sky/plugin/api/ServicePlugin";
    static final String VOID_CLASS_NAME = "java.lang.Void";

    Map<String, PluginClassMetadata> scan(JarFile jarFile) throws IOException {
        Map<String, PluginClassMetadata> metadata = new LinkedHashMap<>();
        for (JarEntry entry : jarFile.stream().sorted(Comparator.comparing(JarEntry::getName)).toList()) {
            if (entry.isDirectory() || !entry.getName().endsWith(".class") || entry.getName().equals("module-info.class") || entry.getName().endsWith("/package-info.class")) {
                continue;
            }
            try (InputStream inputStream = jarFile.getInputStream(entry)) {
                PluginClassVisitor visitor = new PluginClassVisitor();
                new ClassReader(inputStream).accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                PluginClassMetadata classMetadata = visitor.metadata();
                metadata.put(classMetadata.className(), classMetadata);
            }
        }
        return metadata;
    }

    private static String className(String internalName) {
        return internalName == null ? null : internalName.replace('/', '.');
    }

    private static final class PluginClassVisitor extends ClassVisitor {
        private String className;
        private String superName;
        private List<String> interfaces = List.of();
        private SkyPluginMetadata skyPlugin;
        private PluginConfigMetadata pluginConfig;
        private PluginAccountMetadata pluginAccount;
        private final List<FieldMetadata> fields = new ArrayList<>();

        private PluginClassVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = className(name);
            this.superName = className(superName);
            this.interfaces = interfaces == null ? List.of() : List.of(interfaces);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (SKY_PLUGIN_DESCRIPTOR.equals(descriptor)) {
                SkyPluginMetadata.Builder builder = new SkyPluginMetadata.Builder();
                return new SkyPluginAnnotationVisitor(builder, value -> this.skyPlugin = value);
            }
            if (PLUGIN_CONFIG_DESCRIPTOR.equals(descriptor)) {
                PluginConfigMetadata.Builder builder = new PluginConfigMetadata.Builder();
                return new PluginConfigAnnotationVisitor(builder, value -> this.pluginConfig = value);
            }
            if (PLUGIN_ACCOUNT_DESCRIPTOR.equals(descriptor)) {
                PluginAccountMetadata.Builder builder = new PluginAccountMetadata.Builder();
                return new PluginAccountAnnotationVisitor(builder, value -> this.pluginAccount = value);
            }
            return null;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            return new PluginFieldVisitor(name, fields::add);
        }

        PluginClassMetadata metadata() {
            return new PluginClassMetadata(className, superName, interfaces, skyPlugin, pluginConfig, pluginAccount, List.copyOf(fields));
        }
    }

    private static final class PluginFieldVisitor extends FieldVisitor {
        private final String fieldName;
        private final MetadataSink<FieldMetadata> sink;
        private ConfigFieldMetadata configField;
        private AccountFieldMetadata accountField;
        private boolean accountId;

        private PluginFieldVisitor(String fieldName, MetadataSink<FieldMetadata> sink) {
            super(Opcodes.ASM9);
            this.fieldName = fieldName;
            this.sink = sink;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (CONFIG_FIELD_DESCRIPTOR.equals(descriptor)) {
                ConfigFieldMetadata.Builder builder = new ConfigFieldMetadata.Builder();
                return new ConfigFieldAnnotationVisitor(builder, value -> this.configField = value);
            }
            if (ACCOUNT_FIELD_DESCRIPTOR.equals(descriptor)) {
                AccountFieldMetadata.Builder builder = new AccountFieldMetadata.Builder();
                return new AccountFieldAnnotationVisitor(builder, value -> this.accountField = value);
            }
            if (ACCOUNT_ID_DESCRIPTOR.equals(descriptor)) {
                this.accountId = true;
                return new AnnotationVisitor(Opcodes.ASM9) {
                };
            }
            return null;
        }

        @Override
        public void visitEnd() {
            sink.accept(new FieldMetadata(fieldName, configField, accountField, accountId));
        }
    }

    private static final class SkyPluginAnnotationVisitor extends AnnotationVisitor {
        private final SkyPluginMetadata.Builder builder;
        private final MetadataSink<SkyPluginMetadata> sink;

        private SkyPluginAnnotationVisitor(SkyPluginMetadata.Builder builder, MetadataSink<SkyPluginMetadata> sink) {
            super(Opcodes.ASM9);
            this.builder = builder;
            this.sink = sink;
        }

        @Override
        public void visit(String name, Object value) {
            switch (name) {
                case "pluginId" -> builder.pluginId = value.toString();
                case "version" -> builder.version = value.toString();
                case "displayName" -> builder.displayName = value.toString();
                case "description" -> builder.description = value.toString();
                case "configClass" -> builder.configClass = ((Type) value).getClassName();
                case "accountClass" -> builder.accountClass = ((Type) value).getClassName();
                default -> {
                }
            }
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            if ("runtime".equals(name)) {
                builder.runtime = value;
            }
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            if (!"capabilities".equals(name)) {
                return null;
            }
            builder.capabilities = new ArrayList<>();
            return new AnnotationVisitor(Opcodes.ASM9) {
                @Override
                public void visitEnum(String name, String descriptor, String value) {
                    builder.capabilities.add(value);
                }
            };
        }

        @Override
        public void visitEnd() {
            sink.accept(builder.build());
        }
    }

    private static final class PluginConfigAnnotationVisitor extends AnnotationVisitor {
        private final PluginConfigMetadata.Builder builder;
        private final MetadataSink<PluginConfigMetadata> sink;

        private PluginConfigAnnotationVisitor(PluginConfigMetadata.Builder builder, MetadataSink<PluginConfigMetadata> sink) {
            super(Opcodes.ASM9);
            this.builder = builder;
            this.sink = sink;
        }

        @Override
        public void visit(String name, Object value) {
            switch (name) {
                case "value" -> builder.value = value.toString();
                case "displayName" -> builder.displayName = value.toString();
                default -> {
                }
            }
        }

        @Override
        public void visitEnd() {
            sink.accept(builder.build());
        }
    }

    private static final class PluginAccountAnnotationVisitor extends AnnotationVisitor {
        private final PluginAccountMetadata.Builder builder;
        private final MetadataSink<PluginAccountMetadata> sink;

        private PluginAccountAnnotationVisitor(PluginAccountMetadata.Builder builder, MetadataSink<PluginAccountMetadata> sink) {
            super(Opcodes.ASM9);
            this.builder = builder;
            this.sink = sink;
        }

        @Override
        public void visit(String name, Object value) {
            switch (name) {
                case "value" -> builder.value = value.toString();
                case "displayName" -> builder.displayName = value.toString();
                default -> {
                }
            }
        }

        @Override
        public void visitEnd() {
            sink.accept(builder.build());
        }
    }

    private static final class ConfigFieldAnnotationVisitor extends AnnotationVisitor {
        private final ConfigFieldMetadata.Builder builder;
        private final MetadataSink<ConfigFieldMetadata> sink;

        private ConfigFieldAnnotationVisitor(ConfigFieldMetadata.Builder builder, MetadataSink<ConfigFieldMetadata> sink) {
            super(Opcodes.ASM9);
            this.builder = builder;
            this.sink = sink;
        }

        @Override
        public void visit(String name, Object value) {
            switch (name) {
                case "label" -> builder.label = value.toString();
                case "required" -> builder.required = (Boolean) value;
                case "itemClass" -> builder.itemClass = ((Type) value).getClassName();
                case "visibleWhenField" -> builder.visibleWhenField = value.toString();
                case "visibleWhenEquals" -> builder.visibleWhenEquals = value.toString();
                default -> {
                }
            }
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            if ("type".equals(name)) {
                builder.type = value;
            }
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            if ("options".equals(name)) {
                builder.options = new ArrayList<>();
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String name, Object value) {
                        builder.options.add(value.toString());
                    }
                };
            }
            if ("visibleWhenIn".equals(name)) {
                builder.visibleWhenIn = new ArrayList<>();
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String name, Object value) {
                        builder.visibleWhenIn.add(value.toString());
                    }
                };
            }
            return null;
        }

        @Override
        public void visitEnd() {
            sink.accept(builder.build());
        }
    }

    private static final class AccountFieldAnnotationVisitor extends AnnotationVisitor {
        private final AccountFieldMetadata.Builder builder;
        private final MetadataSink<AccountFieldMetadata> sink;

        private AccountFieldAnnotationVisitor(AccountFieldMetadata.Builder builder, MetadataSink<AccountFieldMetadata> sink) {
            super(Opcodes.ASM9);
            this.builder = builder;
            this.sink = sink;
        }

        @Override
        public void visit(String name, Object value) {
            switch (name) {
                case "label" -> builder.label = value.toString();
                case "required" -> builder.required = (Boolean) value;
                case "sensitive" -> builder.sensitive = (Boolean) value;
                case "readonly" -> builder.readonly = (Boolean) value;
                case "itemClass" -> builder.itemClass = ((Type) value).getClassName();
                case "visibleWhenField" -> builder.visibleWhenField = value.toString();
                case "visibleWhenEquals" -> builder.visibleWhenEquals = value.toString();
                default -> {
                }
            }
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            if ("type".equals(name)) {
                builder.type = value;
            }
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            if ("options".equals(name)) {
                builder.options = new ArrayList<>();
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String name, Object value) {
                        builder.options.add(value.toString());
                    }
                };
            }
            if ("visibleWhenIn".equals(name)) {
                builder.visibleWhenIn = new ArrayList<>();
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String name, Object value) {
                        builder.visibleWhenIn.add(value.toString());
                    }
                };
            }
            return null;
        }

        @Override
        public void visitEnd() {
            sink.accept(builder.build());
        }
    }

    record PluginClassMetadata(
            String className,
            String superName,
            List<String> interfaces,
            SkyPluginMetadata skyPlugin,
            PluginConfigMetadata pluginConfig,
            PluginAccountMetadata pluginAccount,
            List<FieldMetadata> fields
    ) {
        boolean directlyImplementsTaskPlugin() {
            return interfaces.contains(TASK_PLUGIN_INTERNAL_NAME);
        }

        boolean directlyImplementsServicePlugin() {
            return interfaces.contains(SERVICE_PLUGIN_INTERNAL_NAME);
        }
    }

    record SkyPluginMetadata(
            String pluginId,
            String version,
            String displayName,
            String description,
            String runtime,
            List<String> capabilities,
            String configClass,
            String accountClass
    ) {
        private static final class Builder {
            private String pluginId;
            private String version;
            private String displayName = "";
            private String description = "";
            private String runtime = "JAVA_PROCESS";
            private List<String> capabilities = new ArrayList<>(List.of("TASK"));
            private String configClass = VOID_CLASS_NAME;
            private String accountClass = VOID_CLASS_NAME;

            private SkyPluginMetadata build() {
                return new SkyPluginMetadata(pluginId, version, displayName, description, runtime, List.copyOf(capabilities), configClass, accountClass);
            }
        }
    }

    record PluginConfigMetadata(String value, String displayName) {
        private static final class Builder {
            private String value = "";
            private String displayName = "";

            private PluginConfigMetadata build() {
                return new PluginConfigMetadata(value, displayName);
            }
        }
    }

    record PluginAccountMetadata(String value, String displayName) {
        private static final class Builder {
            private String value = "";
            private String displayName = "";

            private PluginAccountMetadata build() {
                return new PluginAccountMetadata(value, displayName);
            }
        }
    }

    record FieldMetadata(String name, ConfigFieldMetadata configField, AccountFieldMetadata accountField, boolean accountId) {
    }

    record ConfigFieldMetadata(String label, String type, boolean required, List<String> options, String itemClass, String visibleWhenField, String visibleWhenEquals, List<String> visibleWhenIn) {
        private static final class Builder {
            private String label;
            private String type = "TEXT";
            private boolean required;
            private List<String> options = new ArrayList<>();
            private String itemClass = VOID_CLASS_NAME;
            private String visibleWhenField = "";
            private String visibleWhenEquals = "";
            private List<String> visibleWhenIn = new ArrayList<>();

            private ConfigFieldMetadata build() {
                return new ConfigFieldMetadata(label, type, required, List.copyOf(options), itemClass, visibleWhenField, visibleWhenEquals, List.copyOf(visibleWhenIn));
            }
        }
    }

    record AccountFieldMetadata(
            String label,
            String type,
            boolean required,
            boolean sensitive,
            boolean readonly,
            List<String> options,
            String itemClass,
            String visibleWhenField,
            String visibleWhenEquals,
            List<String> visibleWhenIn
    ) {
        private static final class Builder {
            private String label;
            private String type = "TEXT";
            private boolean required;
            private boolean sensitive;
            private boolean readonly;
            private List<String> options = new ArrayList<>();
            private String itemClass = VOID_CLASS_NAME;
            private String visibleWhenField = "";
            private String visibleWhenEquals = "";
            private List<String> visibleWhenIn = new ArrayList<>();

            private AccountFieldMetadata build() {
                return new AccountFieldMetadata(
                        label,
                        type,
                        required,
                        sensitive,
                        readonly,
                        List.copyOf(options),
                        itemClass,
                        visibleWhenField,
                        visibleWhenEquals,
                        List.copyOf(visibleWhenIn)
                );
            }
        }
    }

    @FunctionalInterface
    private interface MetadataSink<T> {
        void accept(T value);
    }
}
