package com.ainote.app.service;

import com.ainote.app.entity.PasswordResetToken;
import com.ainote.app.entity.User;
import com.ainote.app.repository.PasswordResetTokenRepository;
import com.ainote.app.repository.UserRepository;
import com.ainote.app.security.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final int RESET_TOKEN_TTL_MINUTES = 15;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final PasswordResetNotifier notifier;
    private final AuthAuditLogger authAuditLogger;
    private final AuthRateLimiter authRateLimiter;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                TokenService tokenService,
                                PasswordResetNotifier notifier,
                                AuthAuditLogger authAuditLogger,
                                AuthRateLimiter authRateLimiter) {
        this(userRepository, tokenRepository, passwordEncoder, tokenService, notifier,
                authAuditLogger, authRateLimiter, Clock.systemUTC());
    }

    PasswordResetService(UserRepository userRepository,
                         PasswordResetTokenRepository tokenRepository,
                         PasswordEncoder passwordEncoder,
                         TokenService tokenService,
                         PasswordResetNotifier notifier,
                         AuthAuditLogger authAuditLogger,
                         AuthRateLimiter authRateLimiter,
                         Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.notifier = notifier;
        this.authAuditLogger = authAuditLogger;
        this.authRateLimiter = authRateLimiter;
        this.clock = clock;
    }

    public void requestReset(String email, String ipAddress, String userAgent) {
        authRateLimiter.check("password-reset-request", email + ":" + ipAddress, 5);

        Optional<User> user = userRepository.findByEmail(email);
        authAuditLogger.passwordResetRequested(email, ipAddress, user.isPresent());
        if (user.isEmpty()) {
            return;
        }

        String rawToken = generateRawToken();
        LocalDateTime now = now();

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setId(UUID.randomUUID().toString());
        resetToken.setUser(user.get());
        resetToken.setTokenHash(hashTokenForStorage(rawToken));
        resetToken.setCreatedAt(now);
        resetToken.setExpiresAt(now.plusMinutes(RESET_TOKEN_TTL_MINUTES));
        resetToken.setRequestIp(ipAddress);
        resetToken.setUserAgent(userAgent);
        resetToken.setRawTokenForDelivery(rawToken);

        tokenRepository.save(resetToken);
        notifier.sendPasswordResetToken(user.get(), resetToken.getRawTokenForDelivery());
    }

    public void confirmReset(String rawToken, String newPassword) {
        authRateLimiter.check("password-reset-confirm", rawToken, 10);

        PasswordResetToken token = tokenRepository
                .findByTokenHashAndUsedAtIsNull(hashTokenForStorage(rawToken))
                .orElseThrow(() -> invalidToken("not_found"));

        LocalDateTime now = now();
        if (!token.getExpiresAt().isAfter(now)) {
            throw invalidToken("expired");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        token.setUsedAt(now);

        userRepository.save(user);
        tokenRepository.save(token);
        tokenService.deleteAllTokensForUser(user.getId());
        authAuditLogger.passwordResetConfirmed(user.getId());
    }

    public static String hashTokenForStorage(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private IllegalArgumentException invalidToken(String reason) {
        authAuditLogger.passwordResetRejected(reason);
        return new IllegalArgumentException("Invalid or expired reset token");
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }
}
