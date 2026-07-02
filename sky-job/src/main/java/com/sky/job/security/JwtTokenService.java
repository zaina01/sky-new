package com.sky.job.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import com.sky.job.system.DynamicSecuritySettings;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class JwtTokenService {
    private final SkySecurityProperties properties;
    private final DynamicSecuritySettings securitySettings;
    private final SecretKey signingKey;

    public JwtTokenService(SkySecurityProperties properties, DynamicSecuritySettings securitySettings) {
        this.properties = properties;
        this.securitySettings = securitySettings;
        if (properties.jwtSecret().isBlank() || properties.jwtSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("SKY_JWT_SECRET must be at least 32 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(securitySettings.jwtExpiration());
        Map<String, Object> claims = Map.of(
                "uid", user.userId(),
                "roles", List.copyOf(user.roles()),
                "permissions", List.copyOf(user.permissions())
        );
        return Jwts.builder()
                .claims(claims)
                .issuer(properties.jwtIssuer())
                .subject(user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public String username(String token) {
        return claims(token).getSubject();
    }

    public boolean validate(String token, AuthenticatedUser user) {
        Claims claims = claims(token);
        return user.isEnabled()
                && user.getUsername().equals(claims.getSubject())
                && properties.jwtIssuer().equals(claims.getIssuer())
                && claims.getExpiration().after(new Date());
    }

    public long expiresInSeconds() {
        return securitySettings.jwtExpiration();
    }

    private Claims claims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.jwtIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
