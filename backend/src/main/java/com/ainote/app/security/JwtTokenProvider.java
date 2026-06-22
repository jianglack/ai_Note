package com.ainote.app.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final int MIN_SECRET_BYTES = 32;
    private static final String INSECURE_DEFAULT_SECRET =
            "ainote-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm-security";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    @Value("${app.jwt.issuer}")
    private String jwtIssuer;

    @PostConstruct
    public void validateConfiguration() {
        if (!StringUtils.hasText(jwtSecret)) {
            throw new IllegalStateException("JWT_SECRET must be configured");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        if (INSECURE_DEFAULT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException("JWT_SECRET must not use the development default");
        }
        if (!StringUtils.hasText(jwtIssuer)) {
            throw new IllegalStateException("JWT_ISSUER must be configured");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String userId, String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .issuer(jwtIssuer)
                .signWith(getSigningKey())
                .compact();
    }

    public String getUserIdFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).get("username", String.class);
    }

    public String getJtiFromToken(String token) {
        return parseClaims(token).getId();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .requireIssuer(jwtIssuer)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(jwtIssuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
