package com.ainote.app.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AuthRateLimiter {

    private final Cache<String, AtomicInteger> attempts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(10_000)
            .build();

    public void check(String scope, String key, int maxAttempts) {
        String cacheKey = scope + ":" + normalize(key);
        int count = attempts.get(cacheKey, ignored -> new AtomicInteger()).incrementAndGet();
        if (count > maxAttempts) {
            throw new RateLimitExceededException("Too many authentication attempts");
        }
    }

    private String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }
}
