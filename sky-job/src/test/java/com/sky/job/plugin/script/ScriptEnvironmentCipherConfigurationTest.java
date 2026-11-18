package com.sky.job.plugin.script;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScriptEnvironmentCipherConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> {
                var sources = context.getEnvironment().getPropertySources();
                sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                try {
                    for (var source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))) {
                        sources.addLast(source);
                    }
                } catch (java.io.IOException e) {
                    throw new IllegalStateException(e);
                }
            })
            .withUserConfiguration(ScriptEnvironmentCipher.class);

    @Test
    void missingEnvironmentKeyNeverFallsBackToASharedKey() {
        runner.run(context -> assertThatThrownBy(() -> context.getBean(ScriptEnvironmentCipher.class)
                .encrypt("private account", "installation:variable"))
                .hasMessageContaining("SKY_SCRIPT_ENV_ENCRYPTION_KEY"));
    }

    @Test
    void suppliedEnvironmentKeyDecryptsAfterRecreatingTheContext() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        String[] encrypted = new String[1];
        runner.withPropertyValues("SKY_SCRIPT_ENV_ENCRYPTION_KEY=" + key).run(context -> {
            encrypted[0] = context.getBean(ScriptEnvironmentCipher.class).encrypt("private account", "installation:variable");
            assertThat(encrypted[0]).doesNotContain("private account");
        });
        runner.withPropertyValues("SKY_SCRIPT_ENV_ENCRYPTION_KEY=" + key).run(context ->
                assertThat(context.getBean(ScriptEnvironmentCipher.class).decrypt(encrypted[0], "installation:variable"))
                        .isEqualTo("private account"));
        runner.withPropertyValues("SKY_SCRIPT_ENV_ENCRYPTION_KEY=" + Base64.getEncoder().encodeToString(new byte[31])).run(context ->
                assertThatThrownBy(() -> context.getBean(ScriptEnvironmentCipher.class).decrypt(encrypted[0], "installation:variable"))
                        .hasMessageContaining("SKY_SCRIPT_ENV_ENCRYPTION_KEY"));
    }
}
