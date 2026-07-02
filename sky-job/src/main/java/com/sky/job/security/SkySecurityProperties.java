package com.sky.job.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sky.security")
public record SkySecurityProperties(
        String jwtSecret,
        long jwtExpiration,
        String jwtIssuer
) {
    public SkySecurityProperties {
        if (jwtExpiration <= 0) jwtExpiration = 86400;
        if (jwtIssuer == null || jwtIssuer.isBlank()) jwtIssuer = "sky-new";
        if (jwtSecret == null) jwtSecret = "";
    }
}
