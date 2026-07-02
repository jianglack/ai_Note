package com.ainote.app.service;

import com.ainote.app.entity.PasswordResetToken;
import com.ainote.app.entity.User;
import com.ainote.app.repository.PasswordResetTokenRepository;
import com.ainote.app.repository.UserRepository;
import com.ainote.app.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
class PasswordResetServiceTest {
    private UserRepository userRepository;    private PasswordResetTokenRepository tokenRepository;    private PasswordEncoder passwordEncoder;    private TokenService tokenService;    private PasswordResetNotifier notifier;    private AuthAuditLogger authAuditLogger;    private AuthRateLimiter authRateLimiter;
    private PasswordResetService passwordResetService;

    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenRepository = mock(PasswordResetTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        tokenService = mock(TokenService.class);
        notifier = mock(PasswordResetNotifier.class);
        authAuditLogger = mock(AuthAuditLogger.class);
        authRateLimiter = mock(AuthRateLimiter.class);
        passwordResetService = new PasswordResetService(
                userRepository,
                tokenRepository,
                passwordEncoder,
                tokenService,
                notifier,
                authAuditLogger,
                authRateLimiter,
                Clock.fixed(Instant.parse("2026-06-16T10:00:00Z"), ZoneOffset.UTC));

        user = new User();
        user.setId("user-1");
        user.setUsername("victim");
        user.setEmail("victim@example.com");
        user.setPasswordHash("old-hash");
        user.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void requestReset_neverReturnsTokenButStoresOnlyTokenHash() {
        when(userRepository.findByEmail("victim@example.com")).thenReturn(Optional.of(user));

        passwordResetService.requestReset("victim@example.com", "127.0.0.1", "JUnit");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();

        assertThat(savedToken.getUser()).isSameAs(user);
        assertThat(savedToken.getTokenHash()).isNotBlank();
        assertThat(savedToken.getTokenHash()).doesNotContain("reset");
        assertThat(savedToken.getUsedAt()).isNull();
        assertThat(savedToken.getExpiresAt())
                .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2026-06-16T10:15:00Z"), ZoneOffset.UTC));
        verify(notifier).sendPasswordResetToken(user, savedToken.getRawTokenForDelivery());
    }

    @Test
    void requestReset_doesNotRevealUnknownEmail() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        passwordResetService.requestReset("missing@example.com", "127.0.0.1", "JUnit");

        verify(tokenRepository, never()).save(any());
        verify(notifier, never()).sendPasswordResetToken(any(), any());
    }

    @Test
    void confirmReset_updatesPasswordMarksTokenUsedAndRevokesAllExistingTokens() {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(PasswordResetService.hashTokenForStorage("raw-token"));
        token.setExpiresAt(LocalDateTime.ofInstant(Instant.parse("2026-06-16T10:15:00Z"), ZoneOffset.UTC));
        when(tokenRepository.findByTokenHashAndUsedAtIsNull(PasswordResetService.hashTokenForStorage("raw-token")))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.encode("newpassword123")).thenReturn("new-hash");

        passwordResetService.confirmReset("raw-token", "newpassword123");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(token.getUsedAt())
                .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2026-06-16T10:00:00Z"), ZoneOffset.UTC));
        verify(tokenService).deleteAllTokensForUser("user-1");
        verify(tokenRepository).save(token);
        verify(userRepository).save(user);
    }

    @Test
    void confirmReset_rejectsExpiredTokenWithoutChangingPassword() {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(PasswordResetService.hashTokenForStorage("raw-token"));
        token.setExpiresAt(LocalDateTime.ofInstant(Instant.parse("2026-06-16T09:59:00Z"), ZoneOffset.UTC));
        when(tokenRepository.findByTokenHashAndUsedAtIsNull(PasswordResetService.hashTokenForStorage("raw-token")))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordResetService.confirmReset("raw-token", "newpassword123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid or expired reset token");

        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        verify(tokenService, never()).deleteAllTokensForUser(any());
        verify(userRepository, never()).save(any());
    }
}
