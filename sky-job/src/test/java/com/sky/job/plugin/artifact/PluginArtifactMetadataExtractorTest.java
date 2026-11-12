package com.sky.job.plugin.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginArtifactMetadataExtractorTest {
    private final PluginArtifactMetadataExtractor extractor = new PluginArtifactMetadataExtractor(new ObjectMapper());

    @TempDir
    Path tempDir;

    @Test
    void extractsMetadataWithoutLoadingPluginClasses() throws Exception {
        Path artifact = compilePluginJar(Map.of(
                "demo.TestPlugin", """
                        package demo;
                        import com.sky.plugin.api.*;
                        @SkyPlugin(
                            pluginId = \"demo\",
                            version = \"1.0.0\",
                            displayName = \"Demo\",
                            capabilities = {PluginCapability.TASK, PluginCapability.CONFIG, PluginCapability.ACCOUNT},
                            configClass = DemoConfig.class,
                            accountClass = DemoAccount.class
                        )
                        public class TestPlugin implements TaskPlugin {
                            static final String MUST_NOT_INITIALIZE = failIfInitialized();
                            static String failIfInitialized() { throw new RuntimeException(\"must not initialize\"); }
                            public TaskResult run(TaskExecutionContext context) { return TaskResult.success(\"ok\"); }
                        }
                        """,
                "demo.DemoConfig", """
                        package demo;
                        import com.sky.plugin.api.*;
                        @PluginConfig
                        public class DemoConfig {
                            @ConfigField(label = \"Message\")
                            private String message;
                        }
                        """,
                "demo.DemoAccount", """
                        package demo;
                        import com.sky.plugin.api.*;
                        @PluginAccount
                        public class DemoAccount {
                            @AccountId
                            @AccountField(label = \"Account ID\", required = true, readonly = true)
                            private String accountId;
                            @AccountField(label = \"Secret\", type = AccountFieldType.PASSWORD, sensitive = true)
                            private String secret;
                        }
                        """
        ), "demo.TestPlugin");

        PluginUploadManifest manifest = extractor.extract(artifact);

        assertThat(manifest.pluginId()).isEqualTo("demo");
        assertThat(manifest.version()).isEqualTo("1.0.0");
        assertThat(manifest.displayName()).isEqualTo("Demo");
        assertThat(manifest.runtime().type()).isEqualTo("java-process");
        assertThat(manifest.runtime().mainClass()).isEqualTo("demo.TestPlugin");
        assertThat(manifest.capabilities().task()).isTrue();
        assertThat(manifest.capabilities().config()).isTrue();
        assertThat(manifest.capabilities().account()).isTrue();
        assertThat(manifest.capabilities().notification()).isFalse();
        assertThat(manifest.capabilities().service()).isFalse();
        assertThat(manifest.configSchema().path("fields").get(0).path("name").asText()).isEqualTo("message");
        assertThat(manifest.configSchema().path("fields").get(0).path("type").asText()).isEqualTo("TEXT");
        assertThat(manifest.configSchema().path("fields").get(0).path("required").asBoolean()).isFalse();
        assertThat(manifest.configSchema().path("fields").get(0).has("visibleWhen")).isFalse();
        assertThat(manifest.accountSchema().path("accountIdField").asText()).isEqualTo("accountId");
        assertThat(manifest.accountSchema().path("fields").get(0).path("accountId").asBoolean()).isTrue();
        assertThat(manifest.accountSchema().path("fields").get(1).path("type").asText()).isEqualTo("PASSWORD");
        assertThat(manifest.accountSchema().path("fields").get(1).path("sensitive").asBoolean()).isTrue();
    }

    @Test
    void extractsAdditionalConfigFieldTypes() throws Exception {
        Path artifact = compilePluginJar(Map.of(
                "demo.TestPlugin", """
                        package demo;
                        import com.sky.plugin.api.*;
                        @SkyPlugin(pluginId = \"demo\", version = \"1.0.0\", capabilities = {PluginCapability.TASK, PluginCapability.CONFIG}, configClass = DemoConfig.class)
                        public class TestPlugin implements TaskPlugin {
                            public TaskResult run(TaskExecutionContext context) { return TaskResult.success(\"ok\"); }
                        }
                        """,
                "demo.DemoConfig", """
                        package demo;
                        import com.sky.plugin.api.*;
                        @PluginConfig
                        public class DemoConfig {
                            @ConfigField(label = \"Amount\", type = ConfigFieldType.NUMBER)
                            private String amount;
                            @ConfigField(label = \"Secret\", type = ConfigFieldType.PASSWORD)
                            private String secret;
                            @ConfigField(label = \"Mode\", type = ConfigFieldType.SELECT, options = {\"a\", \"b\"})
                            private String mode;
                            @ConfigField(label = \"Tags\", type = ConfigFieldType.MULTI_SELECT, options = {\"x\", \"y\"})
                            private String tags;
                            @ConfigField(label = \"Start Date\", type = ConfigFieldType.DATE)
                            private String startDate;
                            @ConfigField(label = \"Start Time\", type = ConfigFieldType.TIME)
                            private String startTime;
                            @ConfigField(label = \"Run At\", type = ConfigFieldType.DATETIME)
                            private String runAt;
                        }
                        """
        ), "demo.TestPlugin");

        PluginUploadManifest manifest = extractor.extract(artifact);

        assertThat(manifest.configSchema().path("fields")).hasSize(7);
        assertThat(manifest.configSchema().path("fields").findValuesAsText("type"))
                .containsExactly("NUMBER", "PASSWORD", "SELECT", "MULTI_SELECT", "DATE", "TIME", "DATETIME");
    }

    @Test
    void extractsAdditionalAccountFieldTypesAndConditionalFields() throws Exception {
        Path artifact = compileAccountPlugin("""
                package demo;
                import com.sky.plugin.api.*;
                import java.util.List;
                @PluginAccount
                public class DemoAccount {
                    @AccountId
                    @AccountField(label = "Account ID", required = true, readonly = true)
                    private String accountId;
                    @AccountField(label = "Mode", type = AccountFieldType.SELECT, options = {"password", "token"})
                    private String mode;
                    @AccountField(label = "Tags", type = AccountFieldType.MULTI_SELECT, options = {"primary", "backup"}, visibleWhenField = "mode", visibleWhenIn = {"password", "token"})
                    private List<String> tags;
                    @AccountField(label = "Start Date", type = AccountFieldType.DATE)
                    private String startDate;
                    @AccountField(label = "Start Time", type = AccountFieldType.TIME)
                    private String startTime;
                    @AccountField(label = "Run At", type = AccountFieldType.DATETIME)
                    private String runAt;
                }
                """, Map.of());

        PluginUploadManifest manifest = extractor.extract(artifact);

        assertThat(manifest.accountSchema().path("fields")).hasSize(6);
        assertThat(manifest.accountSchema().path("fields").findValuesAsText("type"))
                .containsExactly("TEXT", "SELECT", "MULTI_SELECT", "DATE", "TIME", "DATETIME");
        assertThat(manifest.accountSchema().path("fields").get(2).path("visibleWhen").path("field").asText()).isEqualTo("mode");
        assertThat(manifest.accountSchema().path("fields").get(2).path("visibleWhen").path("in").get(0).asText()).isEqualTo("password");
        assertThat(manifest.accountSchema().path("fields").get(2).path("visibleWhen").path("in").get(1).asText()).isEqualTo("token");
    }

    @Test
    void extractsObjectListAccountFieldSchema() throws Exception {
        Path artifact = compileAccountPlugin("""
                package demo;
                import com.sky.plugin.api.*;
                import java.util.List;
                @PluginAccount
                public class DemoAccount {
                    @AccountId
                    @AccountField(label = "Account ID", required = true, readonly = true)
                    private String accountId;
                    @AccountField(label = "Enabled", type = AccountFieldType.SWITCH)
                    private Boolean enabled;
                    @AccountField(label = "Credentials", type = AccountFieldType.OBJECT_LIST, required = true, itemClass = Credential.class, visibleWhenField = "enabled", visibleWhenEquals = "true")
                    private List<Credential> credentials;
                    public static class Credential {
                        @AccountField(label = "Kind", type = AccountFieldType.SELECT, options = {"password", "token"})
                        private String kind;
                        @AccountField(label = "Secret", type = AccountFieldType.PASSWORD, sensitive = true, visibleWhenField = "kind", visibleWhenEquals = "token")
                        private String secret;
                        @AccountField(label = "Expires", type = AccountFieldType.DATE, readonly = true)
                        private String expires;
                    }
                }
                """, Map.of());

        PluginUploadManifest manifest = extractor.extract(artifact);

        var objectList = manifest.accountSchema().path("fields").get(2);
        assertThat(objectList.path("type").asText()).isEqualTo("OBJECT_LIST");
        assertThat(objectList.path("required").asBoolean()).isTrue();
        assertThat(objectList.path("visibleWhen").path("equals").isBoolean()).isTrue();
        assertThat(objectList.path("visibleWhen").path("equals").asBoolean()).isTrue();
        assertThat(objectList.path("itemSchema").path("fields")).hasSize(3);
        assertThat(objectList.path("itemSchema").path("fields").findValuesAsText("name"))
                .containsExactly("kind", "secret", "expires");
        assertThat(objectList.path("itemSchema").path("fields").get(1).path("sensitive").asBoolean()).isTrue();
        assertThat(objectList.path("itemSchema").path("fields").get(1).path("visibleWhen").path("equals").asText()).isEqualTo("token");
        assertThat(objectList.path("itemSchema").path("fields").get(2).path("readonly").asBoolean()).isTrue();
    }

    @Test
    void rejectsAccountObjectListWithoutItemClass() throws Exception {
        Path artifact = compileAccountPlugin("""
                package demo;
                import com.sky.plugin.api.*;
                import java.util.List;
                @PluginAccount
                public class DemoAccount {
                    @AccountField(label = "Credentials", type = AccountFieldType.OBJECT_LIST)
                    private List<String> credentials;
                }
                """, Map.of());

        assertThatThrownBy(() -> extractor.extract(artifact))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("account field must declare itemClass");
    }

    @Test
    void extractsNestedObjectListAccountFieldSchema() throws Exception {
        Path artifact = compileAccountPlugin("""
                package demo;
                import com.sky.plugin.api.*;
                import java.util.List;
                @PluginAccount
                public class DemoAccount {
                    @AccountId
                    @AccountField(label = "Account ID", required = true)
                    private String accountId;
                    @AccountField(label = "Credentials", type = AccountFieldType.OBJECT_LIST, itemClass = Credential.class)
                    private List<Credential> credentials;
                    public static class Credential {
                        @AccountField(label = "Custom Headers", type = AccountFieldType.SWITCH)
                        private Boolean customHeaders;
                        @AccountField(label = "Headers", type = AccountFieldType.OBJECT_LIST, required = true, itemClass = Header.class, visibleWhenField = "customHeaders", visibleWhenEquals = "true")
                        private List<Header> headers;
                    }
                    public static class Header {
                        @AccountField(label = "Name", required = true)
                        private String name;
                        @AccountField(label = "Secret", type = AccountFieldType.PASSWORD, sensitive = true)
                        private String secret;
                    }
                }
                """, Map.of());

        PluginUploadManifest manifest = extractor.extract(artifact);

        var credentials = manifest.accountSchema().path("fields").get(1);
        var headers = credentials.path("itemSchema").path("fields").get(1);
        assertThat(credentials.path("type").asText()).isEqualTo("OBJECT_LIST");
        assertThat(headers.path("type").asText()).isEqualTo("OBJECT_LIST");
        assertThat(headers.path("required").asBoolean()).isTrue();
        assertThat(headers.path("visibleWhen").path("field").asText()).isEqualTo("customHeaders");
        assertThat(headers.path("visibleWhen").path("equals").isBoolean()).isTrue();
        assertThat(headers.path("visibleWhen").path("equals").asBoolean()).isTrue();
        assertThat(headers.path("itemSchema").path("fields").findValuesAsText("name"))
                .containsExactly("name", "secret");
        assertThat(headers.path("itemSchema").path("fields").get(0).path("required").asBoolean()).isTrue();
        assertThat(headers.path("itemSchema").path("fields").get(1).path("sensitive").asBoolean()).isTrue();
    }

    @Test
    void rejectsIndirectObjectListAccountItemClassCycle() throws Exception {
        Path artifact = compileAccountPlugin("""
                package demo;
                import com.sky.plugin.api.*;
                import java.util.List;
                @PluginAccount
                public class DemoAccount {
                    @AccountField(label = "Credentials", type = AccountFieldType.OBJECT_LIST, itemClass = Credential.class)
                    private List<Credential> credentials;
                    public static class Credential {
                        @AccountField(label = "Owners", type = AccountFieldType.OBJECT_LIST, itemClass = DemoAccount.class)
                        private List<DemoAccount> owners;
                    }
                }
                """, Map.of());

        assertThatThrownBy(() -> extractor.extract(artifact))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("account itemClass cycle detected")
                .hasMessageContaining("demo.DemoAccount -> demo.DemoAccount$Credential -> demo.DemoAccount");
    }

    @Test
    void extractsAccountObjectListAtMaximumNestingDepth() throws Exception {
        Path artifact = compileAccountPlugin(nestedAccountSource(16), Map.of());

        PluginUploadManifest manifest = extractor.extract(artifact);

        assertNestedObjectListDepth(manifest.accountSchema(), 16);
    }

    @Test
    void rejectsAccountObjectListBeyondMaximumNestingDepth() throws Exception {
        Path artifact = compileAccountPlugin(nestedAccountSource(17), Map.of());

        assertThatThrownBy(() -> extractor.extract(artifact))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("account nesting exceeds maximum depth 16");
    }

    @Test
    void rejectsAccountIdAtAnyObjectListItemDepth() throws Exception {
        Path artifact = compileAccountPlugin("""
                package demo;
                import com.sky.plugin.api.*;
                import java.util.List;
                @PluginAccount
                public class DemoAccount {
                    @AccountField(label = "Groups", type = AccountFieldType.OBJECT_LIST, itemClass = Group.class)
                    private List<Group> groups;
                    public static class Group {
                        @AccountField(label = "Credentials", type = AccountFieldType.OBJECT_LIST, itemClass = Credential.class)
                        private List<Credential> credentials;
                    }
                    public static class Credential {
                        @AccountId
                        @AccountField(label = "Credential ID")
                        private String credentialId;
                    }
                }
                """, Map.of());

        assertThatThrownBy(() -> extractor.extract(artifact))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OBJECT_LIST account item field cannot declare @AccountId");
    }

    @Test
    void rejectsConditionalAccountFieldWithUnknownControlField() throws Exception {
        Path artifact = compileAccountPlugin("""
                package demo;
                import com.sky.plugin.api.*;
                @PluginAccount
                public class DemoAccount {
                    @AccountField(label = "Token", visibleWhenField = "mode", visibleWhenEquals = "token")
                    private String token;
                }
                """, Map.of());

        assertThatThrownBy(() -> extractor.extract(artifact))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("account visibleWhen references unknown field");
    }

    @Test
    void rejectsSwitchConditionalAccountFieldWithNonBooleanValue() throws Exception {
        Path artifact = compileAccountPlugin("""
                package demo;
                import com.sky.plugin.api.*;
                @PluginAccount
                public class DemoAccount {
                    @AccountField(label = "Enabled", type = AccountFieldType.SWITCH)
                    private Boolean enabled;
                    @AccountField(label = "Token", visibleWhenField = "enabled", visibleWhenEquals = "yes")
                    private String token;
                }
                """, Map.of());

        assertThatThrownBy(() -> extractor.extract(artifact))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("account SWITCH visibleWhen value must be true or false");
    }

    @Test
    void extractsObjectListConfigFieldSchema() throws Exception {
        Path artifact = compileConfigPlugin("""
                package demo;
                import com.sky.plugin.api.*;
                import java.util.List;
                @PluginConfig
                public class DemoConfig {
                    @ConfigField(label = \"Enabled\", type = ConfigFieldType.SWITCH)
                    private Boolean enabled;
                    @ConfigField(label = \"Products\", type = ConfigFieldType.OBJECT_LIST, required = true, itemClass = ProductItem.class, visibleWhenField = \"enabled\", visibleWhenEquals = \"true\")
                    private List<ProductItem> products;
                    public static class ProductItem {
                        @ConfigField(label = \"Product Name\", type = ConfigFieldType.TEXT, required = true)
                        private String productName;
                        @ConfigField(label = \"Sort Order\", type = ConfigFieldType.NUMBER, required = true)
                        private Integer sortOrder;
                        @ConfigField(label = \"Use Alias\", type = ConfigFieldType.SWITCH)
                        private Boolean useAlias;
                        @ConfigField(label = \"Alias\", visibleWhenField = \"useAlias\", visibleWhenEquals = \"true\")
                        private String alias;
                    }
                }
                """, Map.of());

        PluginUploadManifest manifest = extractor.extract(artifact);

        assertThat(manifest.configSchema().path("fields")).hasSize(2);
        assertThat(manifest.configSchema().path("fields").get(1).path("name").asText()).isEqualTo("products");
        assertThat(manifest.configSchema().path("fields").get(1).path("type").asText()).isEqualTo("OBJECT_LIST");
        assertThat(manifest.configSchema().path("fields").get(1).path("required").asBoolean()).isTrue();
        assertThat(manifest.configSchema().path("fields").get(1).path("visibleWhen").path("equals").asBoolean()).isTrue();
        assertThat(manifest.configSchema().path("fields").get(1).path("itemSchema").path("fields")).hasSize(4);
        assertThat(manifest.configSchema().path("fields").get(1).path("itemSchema").path("fields").findValuesAsText("name"))
                .containsExactly("productName", "sortOrder", "useAlias", "alias");
        assertThat(manifest.configSchema().path("fields").get(1).path("itemSchema").path("fields").get(1).path("type").asText()).isEqualTo("NUMBER");
        assertThat(manifest.configSchema().path("fields").get(1).path("itemSchema").path("fields").get(3).path("visibleWhen").path("equals").asBoolean()).isTrue();
    }

    @Test
    void extractsObjectListWithIndependentItemClass() throws Exception {
        Path artifact = compileConfigPlugin("""
                package demo;
                import com.sky.plugin.api.*;
                import java.util.List;
                @PluginConfig
                public class DemoConfig {
                    @ConfigField(label = \"Products\", type = ConfigFieldType.OBJECT_LIST, itemClass = ProductItem.class)
                    private List<ProductItem> products;
                }
                """, Map.of(
                "demo.ProductItem", """
                        package demo;
                        import com.sky.plugin.api.*;
                        public class ProductItem {
                            @ConfigField(label = \"Product Name\")
                            private String productName;
                        }
                        """
        ));

        PluginUploadManifest manifest = extractor.extract(artifact);

        assertThat(manifest.configSchema().path("fields").get(0).path("itemSchema").path("fields").get(0).path("name").asText()).isEqualTo("productName");
    }

    @Test
    void rejectsObjectListWithoutItemClass() throws Exception {
        Path artifact = compileConfigPlugin("""
                package demo;
                import com.sky.plugin.api.*;
                import java.util.List;
                @PluginConfig
                public class DemoConfig {
                    @ConfigField(label = \"Products\", type = ConfigFieldType.OBJECT_LIST)
                    private List<String> products;
                }
                """, Map.of());

        assertThatThrownBy(() -> extractor.extract(artifact))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must declare itemClass");
    }

    @Test
    void rejectsObjectListWithItemClassOutsideArtifact() throws Exception {
        Path artifact = compileConfigPlugin("""
                package demo;
                import com.sky.plugin.api.*;
                import java.util.List;
                @PluginConfig
                public class DemoConfig {
                    @ConfigField(label = \"Products\", type = ConfigFieldType.OBJECT_LIST, itemClass = String.class)
                    private List<String> products;
                }
                """, Map.of());

        assertThatThrownBy(() -> extractor.extract(artifact))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("item class not found in artifact");
    }

    @Test
    void rejectsObjectListWithEmptyItemClass() throws Exception {
        Path artifact = compileConfigPlugin("""
                package demo;
                import com.sky.plugin.api.*;
                import java.util.List;
                @PluginConfig
                public class DemoConfig {
                    @ConfigField(label = \"Products\", type = ConfigFieldType.OBJECT_LIST, itemClass = ProductItem.class)
                    private List<ProductItem> products;
                    public static class ProductItem {
                        private String productName;
                    }
                }
                """, Map.of());

        assertThatThrownBy(() -> extractor.extract(artifact))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("item class must declare config fields");
    }

    @Test
    void extractsNestedObjectListConfigFieldSchema() throws Exception {
        Path artifact = compileConfigPlugin("""
                package demo;
                import com.sky.plugin.api.*;
                import java.util.List;
                @PluginConfig
                public class DemoConfig {
                    @ConfigField(label = \"Products\", type = ConfigFieldType.OBJECT_LIST, itemClass = ProductItem.class)
                    private List<ProductItem> products;
                    public static class ProductItem {
                        @ConfigField(label = \"Use Variants\", type = ConfigFieldType.SWITCH)
                        private Boolean useVariants;
                        @ConfigField(label = \"Variants\", type = ConfigFieldType.OBJECT_LIST, required = true, itemClass = VariantItem.class, visibleWhenField = \"useVariants\", visibleWhenEquals = \"true\")
                        private List<VariantItem> variants;
                    }
                    public static class VariantItem {
                        @ConfigField(label = \"Name\", required = true)
                        private String name;
                    }
                }
                """, Map.of());

        PluginUploadManifest manifest = extractor.extract(artifact);

        var products = manifest.configSchema().path("fields").get(0);
        var variants = products.path("itemSchema").path("fields").get(1);
        assertThat(products.path("type").asText()).isEqualTo("OBJECT_LIST");
        assertThat(variants.path("type").asText()).isEqualTo("OBJECT_LIST");
        assertThat(variants.path("required").asBoolean()).isTrue();
        assertThat(variants.path("visibleWhen").path("field").asText()).isEqualTo("useVariants");
        assertThat(variants.path("visibleWhen").path("equals").isBoolean()).isTrue();
        assertThat(variants.path("visibleWhen").path("equals").asBoolean()).isTrue();
        assertThat(variants.path("itemSchema").path("fields").get(0).path("name").asText()).isEqualTo("name");
        assertThat(variants.path("itemSchema").path("fields").get(0).path("required").asBoolean()).isTrue();
    }

    @Test
    void rejectsDirectObjectListConfigItemClassCycle() throws Exception {
        Path artifact = compileConfigPlugin("""
                package demo;
                import com.sky.plugin.api.*;
                import java.util.List;
                @PluginConfig
                public class DemoConfig {
                    @ConfigField(label = "Children", type = ConfigFieldType.OBJECT_LIST, itemClass = DemoConfig.class)
                    private List<DemoConfig> children;
                }
                """, Map.of());

        assertThatThrownBy(() -> extractor.extract(artifact))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config itemClass cycle detected")
                .hasMessageContaining("demo.DemoConfig -> demo.DemoConfig");
    }

    @Test
    void allowsSiblingObjectListsToReuseItemClass() throws Exception {
        Path artifact = compileConfigPlugin("""
                package demo;
                import com.sky.plugin.api.*;
                import java.util.List;
                @PluginConfig
                public class DemoConfig {
                    @ConfigField(label = "Primary", type = ConfigFieldType.OBJECT_LIST, itemClass = SharedItem.class)
                    private List<SharedItem> primary;
                    @ConfigField(label = "Secondary", type = ConfigFieldType.OBJECT_LIST, itemClass = SharedItem.class)
                    private List<SharedItem> secondary;
                    public static class SharedItem {
                        @ConfigField(label = "Value")
                        private String value;
                    }
                }
                """, Map.of());

        PluginUploadManifest manifest = extractor.extract(artifact);

        assertThat(manifest.configSchema().path("fields")).hasSize(2);
        assertThat(manifest.configSchema().path("fields").get(0).path("itemSchema").path("fields").get(0).path("name").asText())
                .isEqualTo("value");
        assertThat(manifest.configSchema().path("fields").get(1).path("itemSchema").path("fields").get(0).path("name").asText())
                .isEqualTo("value");
    }

    @Test
    void extractsConfigObjectListAtMaximumNestingDepth() throws Exception {
        Path artifact = compileConfigPlugin(nestedConfigSource(16), Map.of());

        PluginUploadManifest manifest = extractor.extract(artifact);

        assertNestedObjectListDepth(manifest.configSchema(), 16);
    }

    @Test
    void rejectsConfigObjectListBeyondMaximumNestingDepth() throws Exception {
        Path artifact = compileConfigPlugin(nestedConfigSource(17), Map.of());

        assertThatThrownBy(() -> extractor.extract(artifact))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config nesting exceeds maximum depth 16");
    }

    @Test
    void rejectsObjectListWithOptions() throws Exception {
        Path artifact = compileConfigPlugin("""
                package demo;
                import com.sky.plugin.api.*;
                import java.util.List;
                @PluginConfig
                public class DemoConfig {
                    @ConfigField(label = \"Products\", type = ConfigFieldType.OBJECT_LIST, options = {\"a\"}, itemClass = ProductItem.class)
                    private List<ProductItem> products;
                    public static class ProductItem {
                        @ConfigField(label = \"Name\")
                        private String name;
                    }
                }
                """, Map.of());

        assertThatThrownBy(() -> extractor.extract(artifact))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not declare options");
    }

    @Test
    void rejectsItemClassOnNonObjectListConfigField() throws Exception {
        Path artifact = compileConfigPlugin("""
                package demo;
                import com.sky.plugin.api.*;
                @PluginConfig
                public class DemoConfig {
                    @ConfigField(label = \"Product\", itemClass = ProductItem.class)
                    private String product;
                    public static class ProductItem {
                        @ConfigField(label = \"Name\")
                        private String name;
                    }
                }
                """, Map.of());

        assertThatThrownBy(() -> extractor.extract(artifact))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("itemClass can only be used with OBJECT_LIST");
    }

    @Test
    void extractsServicePluginMetadata() throws Exception {
        Path artifact = compilePluginJar(Map.of(
                "demo.TestPlugin", """
                        package demo;
                        import com.sky.plugin.api.*;
                        @SkyPlugin(pluginId = \"demo-service\", version = \"1.0.0\", capabilities = {PluginCapability.SERVICE, PluginCapability.ACCOUNT})
                        public class TestPlugin implements ServicePlugin {
                            public PluginServiceResponse call(PluginServiceRequest request, TaskExecutionContext context) { return PluginServiceResponse.success(\"{}\"); }
                        }
                        """
        ), "demo.TestPlugin", PluginArtifactMetadataExtractor.SERVICE_SPI_ENTRY);

        PluginUploadManifest manifest = extractor.extract(artifact);

        assertThat(manifest.pluginId()).isEqualTo("demo-service");
        assertThat(manifest.capabilities().task()).isFalse();
        assertThat(manifest.capabilities().account()).isTrue();
        assertThat(manifest.capabilities().service()).isTrue();
    }

    @Test
    void extractsConditionalConfigFields() throws Exception {
        Path artifact = compilePluginJar(Map.of(
                "demo.TestPlugin", """
                        package demo;
                        import com.sky.plugin.api.*;
                        @SkyPlugin(pluginId = \"demo\", version = \"1.0.0\", capabilities = {PluginCapability.TASK, PluginCapability.CONFIG}, configClass = DemoConfig.class)
                        public class TestPlugin implements TaskPlugin {
                            public TaskResult run(TaskExecutionContext context) { return TaskResult.success(\"ok\"); }
                        }
                        """,
                "demo.DemoConfig", """
                        package demo;
                        import com.sky.plugin.api.*;
                        @PluginConfig
                        public class DemoConfig {
                            @ConfigField(label = \"Mode\", type = ConfigFieldType.RADIO, required = true, options = {\"proxy\", \"token\"})
                            private String mode;
                            @ConfigField(label = \"Proxy Host\", visibleWhenField = \"mode\", visibleWhenEquals = \"proxy\")
                            private String proxyHost;
                            @ConfigField(label = \"Token\", visibleWhenField = \"mode\", visibleWhenIn = {\"token\"})
                            private String token;
                        }
                        """
        ), "demo.TestPlugin");

        PluginUploadManifest manifest = extractor.extract(artifact);

        assertThat(manifest.configSchema().path("fields").get(1).path("visibleWhen").path("field").asText()).isEqualTo("mode");
        assertThat(manifest.configSchema().path("fields").get(1).path("visibleWhen").path("equals").asText()).isEqualTo("proxy");
        assertThat(manifest.configSchema().path("fields").get(2).path("visibleWhen").path("field").asText()).isEqualTo("mode");
        assertThat(manifest.configSchema().path("fields").get(2).path("visibleWhen").path("in").get(0).asText()).isEqualTo("token");
    }

    @Test
    void extractsSwitchConditionalConfigFieldsAsBooleans() throws Exception {
        Path artifact = compilePluginJar(Map.of(
                "demo.TestPlugin", """
                        package demo;
                        import com.sky.plugin.api.*;
                        @SkyPlugin(pluginId = \"demo\", version = \"1.0.0\", capabilities = {PluginCapability.TASK, PluginCapability.CONFIG}, configClass = DemoConfig.class)
                        public class TestPlugin implements TaskPlugin {
                            public TaskResult run(TaskExecutionContext context) { return TaskResult.success(\"ok\"); }
                        }
                        """,
                "demo.DemoConfig", """
                        package demo;
                        import com.sky.plugin.api.*;
                        @PluginConfig
                        public class DemoConfig {
                            @ConfigField(label = \"Enabled\", type = ConfigFieldType.SWITCH)
                            private Boolean enabled;
                            @ConfigField(label = \"Token\", visibleWhenField = \"enabled\", visibleWhenEquals = \"true\")
                            private String token;
                            @ConfigField(label = \"Disabled Reason\", visibleWhenField = \"enabled\", visibleWhenIn = {\"false\"})
                            private String disabledReason;
                        }
                        """
        ), "demo.TestPlugin");

        PluginUploadManifest manifest = extractor.extract(artifact);

        assertThat(manifest.configSchema().path("fields").get(1).path("visibleWhen").path("equals").isBoolean()).isTrue();
        assertThat(manifest.configSchema().path("fields").get(1).path("visibleWhen").path("equals").asBoolean()).isTrue();
        assertThat(manifest.configSchema().path("fields").get(2).path("visibleWhen").path("in").get(0).isBoolean()).isTrue();
        assertThat(manifest.configSchema().path("fields").get(2).path("visibleWhen").path("in").get(0).asBoolean()).isFalse();
    }

    @Test
    void rejectsSwitchConditionalConfigFieldWithNonBooleanValue() throws Exception {
        Path artifact = compilePluginJar(Map.of(
                "demo.TestPlugin", """
                        package demo;
                        import com.sky.plugin.api.*;
                        @SkyPlugin(pluginId = \"demo\", version = \"1.0.0\", capabilities = {PluginCapability.TASK, PluginCapability.CONFIG}, configClass = DemoConfig.class)
                        public class TestPlugin implements TaskPlugin {
                            public TaskResult run(TaskExecutionContext context) { return TaskResult.success(\"ok\"); }
                        }
                        """,
                "demo.DemoConfig", """
                        package demo;
                        import com.sky.plugin.api.*;
                        @PluginConfig
                        public class DemoConfig {
                            @ConfigField(label = \"Enabled\", type = ConfigFieldType.SWITCH)
                            private Boolean enabled;
                            @ConfigField(label = \"Token\", visibleWhenField = \"enabled\", visibleWhenEquals = \"yes\")
                            private String token;
                        }
                        """
        ), "demo.TestPlugin");

        assertThatThrownBy(() -> extractor.extract(artifact))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SWITCH visibleWhen value must be true or false");
    }

    @Test
    void rejectsConditionalConfigFieldWithUnknownControlField() throws Exception {
        Path artifact = compilePluginJar(Map.of(
                "demo.TestPlugin", """
                        package demo;
                        import com.sky.plugin.api.*;
                        @SkyPlugin(pluginId = \"demo\", version = \"1.0.0\", capabilities = {PluginCapability.TASK, PluginCapability.CONFIG}, configClass = DemoConfig.class)
                        public class TestPlugin implements TaskPlugin {
                            public TaskResult run(TaskExecutionContext context) { return TaskResult.success(\"ok\"); }
                        }
                        """,
                "demo.DemoConfig", """
                        package demo;
                        import com.sky.plugin.api.*;
                        @PluginConfig
                        public class DemoConfig {
                            @ConfigField(label = \"Proxy Host\", visibleWhenField = \"mode\", visibleWhenEquals = \"proxy\")
                            private String proxyHost;
                        }
                        """
        ), "demo.TestPlugin");

        assertThatThrownBy(() -> extractor.extract(artifact))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("references unknown field");
    }

    @Test
    void rejectsConditionalConfigFieldThatDependsOnItself() throws Exception {
        Path artifact = compilePluginJar(Map.of(
                "demo.TestPlugin", """
                        package demo;
                        import com.sky.plugin.api.*;
                        @SkyPlugin(pluginId = \"demo\", version = \"1.0.0\", capabilities = {PluginCapability.TASK, PluginCapability.CONFIG}, configClass = DemoConfig.class)
                        public class TestPlugin implements TaskPlugin {
                            public TaskResult run(TaskExecutionContext context) { return TaskResult.success(\"ok\"); }
                        }
                        """,
                "demo.DemoConfig", """
                        package demo;
                        import com.sky.plugin.api.*;
                        @PluginConfig
                        public class DemoConfig {
                            @ConfigField(label = \"Mode\", visibleWhenField = \"mode\", visibleWhenEquals = \"proxy\")
                            private String mode;
                        }
                        """
        ), "demo.TestPlugin");

        assertThatThrownBy(() -> extractor.extract(artifact))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot depend on itself");
    }

    @Test
    void rejectsConditionalConfigFieldWithBothEqualsAndIn() throws Exception {
        Path artifact = compilePluginJar(Map.of(
                "demo.TestPlugin", """
                        package demo;
                        import com.sky.plugin.api.*;
                        @SkyPlugin(pluginId = \"demo\", version = \"1.0.0\", capabilities = {PluginCapability.TASK, PluginCapability.CONFIG}, configClass = DemoConfig.class)
                        public class TestPlugin implements TaskPlugin {
                            public TaskResult run(TaskExecutionContext context) { return TaskResult.success(\"ok\"); }
                        }
                        """,
                "demo.DemoConfig", """
                        package demo;
                        import com.sky.plugin.api.*;
                        @PluginConfig
                        public class DemoConfig {
                            @ConfigField(label = \"Mode\")
                            private String mode;
                            @ConfigField(label = \"Proxy Host\", visibleWhenField = \"mode\", visibleWhenEquals = \"proxy\", visibleWhenIn = {\"token\"})
                            private String proxyHost;
                        }
                        """
        ), "demo.TestPlugin");

        assertThatThrownBy(() -> extractor.extract(artifact))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("either equals or in");
    }

    @Test
    void rejectsConditionalConfigFieldWithoutConditionValue() throws Exception {
        Path artifact = compilePluginJar(Map.of(
                "demo.TestPlugin", """
                        package demo;
                        import com.sky.plugin.api.*;
                        @SkyPlugin(pluginId = \"demo\", version = \"1.0.0\", capabilities = {PluginCapability.TASK, PluginCapability.CONFIG}, configClass = DemoConfig.class)
                        public class TestPlugin implements TaskPlugin {
                            public TaskResult run(TaskExecutionContext context) { return TaskResult.success(\"ok\"); }
                        }
                        """,
                "demo.DemoConfig", """
                        package demo;
                        import com.sky.plugin.api.*;
                        @PluginConfig
                        public class DemoConfig {
                            @ConfigField(label = \"Mode\")
                            private String mode;
                            @ConfigField(label = \"Proxy Host\", visibleWhenField = \"mode\")
                            private String proxyHost;
                        }
                        """
        ), "demo.TestPlugin");

        assertThatThrownBy(() -> extractor.extract(artifact))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("condition is required");
    }

    @Test
    void rejectsPluginClassThatDoesNotDirectlyImplementTaskPlugin() throws Exception {
        Path artifact = compilePluginJar(Map.of(
                "demo.BasePlugin", """
                        package demo;
                        import com.sky.plugin.api.*;
                        public abstract class BasePlugin implements TaskPlugin {}
                        """,
                "demo.TestPlugin", """
                        package demo;
                        import com.sky.plugin.api.*;
                        @SkyPlugin(pluginId = \"demo\", version = \"1.0.0\")
                        public class TestPlugin extends BasePlugin {
                            public TaskResult run(TaskExecutionContext context) { return TaskResult.success(\"ok\"); }
                        }
                        """
        ), "demo.TestPlugin");

        assertThatThrownBy(() -> extractor.extract(artifact))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must directly implement TaskPlugin or ServicePlugin");
    }

    private void assertNestedObjectListDepth(JsonNode schema, int expectedDepth) {
        JsonNode field = schema.path("fields").get(0);
        for (int level = 1; level <= expectedDepth; level++) {
            assertThat(field.path("type").asText())
                    .as("OBJECT_LIST type at nesting level %s", level)
                    .isEqualTo("OBJECT_LIST");
            field = field.path("itemSchema").path("fields").get(0);
        }
        assertThat(field.path("name").asText()).isEqualTo("value");
    }

    private String nestedConfigSource(int objectListDepth) {
        return nestedSchemaSource(objectListDepth, true);
    }

    private String nestedAccountSource(int objectListDepth) {
        return nestedSchemaSource(objectListDepth, false);
    }

    private String nestedSchemaSource(int objectListDepth, boolean config) {
        if (objectListDepth < 1) {
            throw new IllegalArgumentException("OBJECT_LIST depth must be positive");
        }
        String rootClass = config ? "DemoConfig" : "DemoAccount";
        String classAnnotation = config ? "PluginConfig" : "PluginAccount";
        String fieldAnnotation = config ? "ConfigField" : "AccountField";
        String fieldType = config ? "ConfigFieldType" : "AccountFieldType";
        StringBuilder source = new StringBuilder()
                .append("package demo;\n")
                .append("import com.sky.plugin.api.*;\n")
                .append("import java.util.List;\n")
                .append('@').append(classAnnotation).append("\n")
                .append("public class ").append(rootClass).append(" {\n")
                .append("    @").append(fieldAnnotation)
                .append("(label = \"Level 1\", type = ").append(fieldType)
                .append(".OBJECT_LIST, itemClass = Level1.class)\n")
                .append("    private List<Level1> level1;\n");
        for (int level = 1; level <= objectListDepth; level++) {
            source.append("    public static class Level").append(level).append(" {\n");
            if (level < objectListDepth) {
                int childLevel = level + 1;
                source.append("        @").append(fieldAnnotation)
                        .append("(label = \"Level ").append(childLevel).append("\", type = ")
                        .append(fieldType).append(".OBJECT_LIST, itemClass = Level")
                        .append(childLevel).append(".class)\n")
                        .append("        private List<Level").append(childLevel).append("> level")
                        .append(childLevel).append(";\n");
            } else {
                source.append("        @").append(fieldAnnotation).append("(label = \"Value\")\n")
                        .append("        private String value;\n");
            }
            source.append("    }\n");
        }
        return source.append("}\n").toString();
    }

    private Path compileConfigPlugin(String configSource, Map<String, String> additionalSources) throws IOException {
        Map<String, String> sources = new java.util.LinkedHashMap<>();
        sources.put("demo.TestPlugin", """
                package demo;
                import com.sky.plugin.api.*;
                @SkyPlugin(pluginId = \"demo\", version = \"1.0.0\", capabilities = {PluginCapability.TASK, PluginCapability.CONFIG}, configClass = DemoConfig.class)
                public class TestPlugin implements TaskPlugin {
                    public TaskResult run(TaskExecutionContext context) { return TaskResult.success(\"ok\"); }
                }
                """);
        sources.put("demo.DemoConfig", configSource);
        sources.putAll(additionalSources);
        return compilePluginJar(sources, "demo.TestPlugin");
    }

    private Path compileAccountPlugin(String accountSource, Map<String, String> additionalSources) throws IOException {
        Map<String, String> sources = new java.util.LinkedHashMap<>();
        sources.put("demo.TestPlugin", """
                package demo;
                import com.sky.plugin.api.*;
                @SkyPlugin(pluginId = "demo", version = "1.0.0", capabilities = {PluginCapability.TASK, PluginCapability.ACCOUNT}, accountClass = DemoAccount.class)
                public class TestPlugin implements TaskPlugin {
                    public TaskResult run(TaskExecutionContext context) { return TaskResult.success("ok"); }
                }
                """);
        sources.put("demo.DemoAccount", accountSource);
        sources.putAll(additionalSources);
        return compilePluginJar(sources, "demo.TestPlugin");
    }

    private String classPath() {
        return java.lang.management.ManagementFactory.getRuntimeMXBean().getClassPath()
                .lines()
                .collect(Collectors.joining(System.getProperty("path.separator")));
    }

    private Path compilePluginJar(Map<String, String> sources, String providerClass) throws IOException {
        return compilePluginJar(sources, providerClass, PluginArtifactMetadataExtractor.TASK_SPI_ENTRY);
    }

    private Path compilePluginJar(Map<String, String> sources, String providerClass, String spiEntry) throws IOException {
        Path sourceRoot = tempDir.resolve("src");
        Path classRoot = tempDir.resolve("classes");
        Files.createDirectories(sourceRoot);
        Files.createDirectories(classRoot);
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path sourceFile = sourceRoot.resolve(source.getKey().replace('.', '/') + ".java");
            Files.createDirectories(sourceFile.getParent());
            Files.writeString(sourceFile, source.getValue(), StandardCharsets.UTF_8);
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        List<String> sourceFiles;
        try (var paths = Files.walk(sourceRoot)) {
            sourceFiles = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(Path::toString)
                    .toList();
        }
        List<String> arguments = new java.util.ArrayList<>();
        arguments.addAll(List.of("-classpath", classPath(), "-d", classRoot.toString()));
        arguments.addAll(sourceFiles);
        int result = compiler.run(null, null, null, arguments.toArray(String[]::new));
        assertThat(result).isZero();

        Path serviceFile = classRoot.resolve(spiEntry);
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, providerClass + System.lineSeparator(), StandardCharsets.UTF_8);

        Path jarFile = tempDir.resolve("plugin.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarFile));
             var paths = Files.walk(classRoot)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString)).toList()) {
                String entryName = classRoot.relativize(path).toString().replace('\\', '/');
                output.putNextEntry(new JarEntry(entryName));
                Files.copy(path, output);
                output.closeEntry();
            }
        }
        return jarFile;
    }
}
