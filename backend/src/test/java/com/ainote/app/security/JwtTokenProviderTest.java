package com.ainote.app.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtTokenProvider 单元测试")
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private final String secret = "ainote-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm-security";
    private final long expiration = 86400000L;
    private final String issuer = "ainote";

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", secret);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpiration", expiration);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtIssuer", issuer);
    }

    @Test
    @DisplayName("generateToken 应生成有效的JWT令牌")
    void shouldGenerateValidToken() {
        String userId = "user-123";
        String username = "testuser";

        String token = jwtTokenProvider.generateToken(userId, username);

        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
    }

    @Test
    @DisplayName("validateToken 应验证有效的令牌")
    void shouldValidateValidToken() {
        String userId = "user-123";
        String username = "testuser";
        String token = jwtTokenProvider.generateToken(userId, username);

        boolean isValid = jwtTokenProvider.validateToken(token);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("validateToken 应拒绝无效的令牌")
    void shouldRejectInvalidToken() {
        String invalidToken = "invalid-token";

        boolean isValid = jwtTokenProvider.validateToken(invalidToken);

        assertThat(isValid).isFalse();
    }

    @Test
    void validateToken_rejectsTokenSignedWithSameSecretButWrongIssuer() {
        Date now = new Date();
        String wrongIssuerToken = Jwts.builder()
                .subject("user-123")
                .claim("username", "testuser")
                .id("jti-123")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .issuer("evil-issuer")
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwtTokenProvider.validateToken(wrongIssuerToken)).isFalse();
    }

    @Test
    @DisplayName("getUserIdFromToken 应从令牌中提取用户ID")
    void shouldExtractUserIdFromToken() {
        String userId = "user-123";
        String username = "testuser";
        String token = jwtTokenProvider.generateToken(userId, username);

        String extractedUserId = jwtTokenProvider.getUserIdFromToken(token);

        assertThat(extractedUserId).isEqualTo(userId);
    }

    @Test
    @DisplayName("getUsernameFromToken 应从令牌中提取用户名")
    void shouldExtractUsernameFromToken() {
        String userId = "user-123";
        String username = "testuser";
        String token = jwtTokenProvider.generateToken(userId, username);

        String extractedUsername = jwtTokenProvider.getUsernameFromToken(token);

        assertThat(extractedUsername).isEqualTo(username);
    }

    @Test
    @DisplayName("getJtiFromToken 应从令牌中提取JTI")
    void shouldExtractJtiFromToken() {
        String userId = "user-123";
        String username = "testuser";
        String token = jwtTokenProvider.generateToken(userId, username);

        String jti = jwtTokenProvider.getJtiFromToken(token);

        assertThat(jti).isNotNull();
        assertThat(jti).isNotEmpty();
    }
}
