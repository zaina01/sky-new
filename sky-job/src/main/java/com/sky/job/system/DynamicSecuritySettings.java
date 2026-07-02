package com.sky.job.system;

import org.springframework.stereotype.Component;

@Component
public class DynamicSecuritySettings {
    private final SystemSettingProvider provider;

    public DynamicSecuritySettings(SystemSettingProvider provider) {
        this.provider = provider;
    }

    public long jwtExpiration() {
        return provider.getLong(SystemSettingDefinitions.SECURITY_JWT_EXPIRATION);
    }
}
