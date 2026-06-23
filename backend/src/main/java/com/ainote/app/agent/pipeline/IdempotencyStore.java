package com.ainote.app.agent.pipeline;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Caffeine 的幂等缓存。
 * 写操作在 5 分钟内按相同 key（userId:toolName:action:paramsDigest）去重，
 * 防止 Agent 循环中意外重复执行破坏性操作。
 */
@Component
public class IdempotencyStore {

    private final Cache<String, Boolean> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(10_000)
            .build();

    /**
     * 检查是否为重复操作。
     * 如果 key 已存在返回 true（重复），否则记录 key 并返回 false（首次）。
     */
    public boolean isDuplicate(String key) {
        return cache.asMap().putIfAbsent(key, Boolean.TRUE) != null;
    }

    /**
     * 按前缀清除缓存（例如清除某用户的全部记录）。
     */
    public void clearByPrefix(String keyPrefix) {
        cache.asMap().keySet().removeIf(k -> k.startsWith(keyPrefix));
    }

    /**
     * 清除指定 key 的缓存（允许用户确认后重新执行）。
     */
    public void invalidate(String key) {
        cache.invalidate(key);
    }
}
