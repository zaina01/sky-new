package com.sky.job.plugin.artifact;

import com.sky.job.plugin.security.PluginCapability;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class PluginUploadManifestValidator {
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_.-]{1,128}");
    private static final Pattern VERSION_PATTERN = Pattern.compile("[A-Za-z0-9_.+-]{1,64}");
    private static final Pattern CLASS_PATTERN = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+");

    public Set<PluginCapability> validate(PluginUploadManifest manifest) {
        if (manifest == null) {
            throw new IllegalArgumentException("Missing plugin manifest");
        }
        require(ID_PATTERN.matcher(nullToEmpty(manifest.pluginId())).matches(), "Invalid pluginId");
        require(VERSION_PATTERN.matcher(nullToEmpty(manifest.version())).matches(), "Invalid plugin version");
        require(manifest.runtime() != null, "Missing runtime descriptor");
        require(Set.of("plugin","standalone").contains(manifest.runtime().executionMode()),"Invalid execution mode");
        require(Set.of("java", "node", "python").contains(manifest.runtime().engine()), "Unsupported plugin runtime type");
        if ("java".equals(manifest.runtime().engine())) {
            require(CLASS_PATTERN.matcher(nullToEmpty(manifest.runtime().mainClass())).matches(), "Invalid plugin main class");
        } else {
            if("standalone".equals(manifest.runtime().executionMode())) {
                require(manifest.runtime().entryPoint().matches("main\\.(js|mjs|py)"),"Invalid standalone entry");
                require(manifest.capabilities()!=null && !manifest.capabilities().config() && !manifest.capabilities().account() && !manifest.capabilities().notification(),"Standalone scripts only support task capability");
            } else com.sky.job.plugin.script.ScriptArchive.validateEntryPoint(manifest.runtime().engine(), manifest.runtime().entryPoint());
            require(manifest.capabilities() != null && manifest.capabilities().task() && !manifest.capabilities().service(), "Script plugins support tasks only");
        }
        require(manifest.configSchema() == null || manifest.configSchema().isObject(), "Plugin config schema must be a JSON object");
        require(manifest.accountSchema() == null || manifest.accountSchema().isObject(), "Plugin account schema must be a JSON object");
        PluginUploadManifest.CapabilityDescriptor capabilities = manifest.capabilities();
        require(capabilities != null, "Missing plugin capabilities");
        require(capabilities.task() || capabilities.service(), "Plugin must declare task or service capability");
        EnumSet<PluginCapability> result = EnumSet.noneOf(PluginCapability.class);
        if (capabilities.task()) result.add(PluginCapability.TASK);
        if (capabilities.config()) result.add(PluginCapability.CONFIG);
        if (capabilities.account()) result.add(PluginCapability.ACCOUNT);
        if (capabilities.notification()) result.add(PluginCapability.NOTIFICATION);
        if (capabilities.service()) result.add(PluginCapability.SERVICE);
        return result;
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
