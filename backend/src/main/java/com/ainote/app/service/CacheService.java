package com.ainote.app.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class CacheService {

    private static final String SUMMARY_PREFIX = "ai:summary:";
    private static final String ACTIONS_PREFIX = "ai:actions:";
    private static final String CHAT_PREFIX = "ai:chat:";

    private final RedisTemplate<String, Object> redisTemplate;

    public CacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void cacheSummary(String noteId, String summary) {
        redisTemplate.opsForValue().set(SUMMARY_PREFIX + noteId, summary, 7, TimeUnit.DAYS);
    }

    public String getCachedSummary(String noteId) {
        Object value = redisTemplate.opsForValue().get(SUMMARY_PREFIX + noteId);
        return value != null ? value.toString() : null;
    }

    public void cacheActions(String noteId, String actions) {
        redisTemplate.opsForValue().set(ACTIONS_PREFIX + noteId, actions, 7, TimeUnit.DAYS);
    }

    public String getCachedActions(String noteId) {
        Object value = redisTemplate.opsForValue().get(ACTIONS_PREFIX + noteId);
        return value != null ? value.toString() : null;
    }

    public void cacheChat(String hash, String response) {
        redisTemplate.opsForValue().set(CHAT_PREFIX + hash, response, 1, TimeUnit.HOURS);
    }

    public String getCachedChat(String hash) {
        Object value = redisTemplate.opsForValue().get(CHAT_PREFIX + hash);
        return value != null ? value.toString() : null;
    }

    public void invalidateNoteCache(String noteId) {
        redisTemplate.delete(SUMMARY_PREFIX + noteId);
        redisTemplate.delete(ACTIONS_PREFIX + noteId);
    }
}
