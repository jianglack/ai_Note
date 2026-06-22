package com.ainote.ai.agent.pipeline;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class IdempotencyStore {

    private final Cache<String, Boolean> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(10_000)
            .build();

    public boolean isDuplicate(String key) {
        if (cache.getIfPresent(key) != null) {
            return true;
        }
        cache.put(key, Boolean.TRUE);
        return false;
    }

    public void clearByPrefix(String keyPrefix) {
        cache.asMap().keySet().removeIf(k -> k.startsWith(keyPrefix));
    }

    public void invalidate(String key) {
        cache.invalidate(key);
    }
}
