package com.ainote.app.service;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRateLimiterTest {

    private AuthRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new AuthRateLimiter();
    }

    @Test
    void check_allowsRequestsUpToThreshold() {
        assertThatCode(() -> {
            limiter.check("login", "user@example.com", 2);
            limiter.check("login", "user@example.com", 2);
        }).doesNotThrowAnyException();
    }

    @Test
    void check_rejectsRequestsAboveThreshold() {
        limiter.check("login", "user@example.com", 2);
        limiter.check("login", "user@example.com", 2);

        assertThatThrownBy(() -> limiter.check("login", "user@example.com", 2))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Too many authentication attempts");
    }

    @Test
    void check_normalizesKeysBeforeCounting() {
        limiter.check("reset", " User@Example.com ", 1);

        assertThatThrownBy(() -> limiter.check("reset", "user@example.com", 1))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void check_afterCacheReset_allowsSameKeyAgain() {
        limiter.check("login", "user@example.com", 1);
        Cache<?, ?> attempts = (Cache<?, ?>) ReflectionTestUtils.getField(limiter, "attempts");
        attempts.invalidateAll();

        assertThatCode(() -> limiter.check("login", "user@example.com", 1))
                .doesNotThrowAnyException();
    }
}
