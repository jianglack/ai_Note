package com.ainote.app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
@DisplayName("CacheService 单元测试")
class CacheServiceTest {
    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOperations;
    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        cacheService = new CacheService(redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("cacheSummary 应缓存摘要 7 天")
    void shouldCacheSummary() {
        cacheService.cacheSummary("note-123", "This is a summary");

        verify(valueOperations).set("ai:summary:note-123", "This is a summary", 7, TimeUnit.DAYS);
    }

    @Test
    @DisplayName("getCachedSummary 应返回缓存的摘要")
    void shouldGetCachedSummary() {
        when(valueOperations.get("ai:summary:note-123")).thenReturn("Cached summary");

        String result = cacheService.getCachedSummary("note-123");

        assertThat(result).isEqualTo("Cached summary");
    }

    @Test
    @DisplayName("getCachedSummary 缓存不存在时返回 null")
    void shouldReturnNullWhenSummaryNotCached() {
        when(valueOperations.get("ai:summary:note-123")).thenReturn(null);

        String result = cacheService.getCachedSummary("note-123");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("cacheActions 应缓存操作 7 天")
    void shouldCacheActions() {
        cacheService.cacheActions("note-123", "Action items");

        verify(valueOperations).set("ai:actions:note-123", "Action items", 7, TimeUnit.DAYS);
    }

    @Test
    @DisplayName("getCachedActions 应返回缓存的操作")
    void shouldGetCachedActions() {
        when(valueOperations.get("ai:actions:note-123")).thenReturn("Cached actions");

        String result = cacheService.getCachedActions("note-123");

        assertThat(result).isEqualTo("Cached actions");
    }

    @Test
    @DisplayName("getCachedActions 缓存不存在时返回 null")
    void shouldReturnNullWhenActionsNotCached() {
        when(valueOperations.get("ai:actions:note-123")).thenReturn(null);

        String result = cacheService.getCachedActions("note-123");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("cacheChat 应缓存聊天 1 小时")
    void shouldCacheChat() {
        cacheService.cacheChat("hash-abc", "Chat response");

        verify(valueOperations).set("ai:chat:hash-abc", "Chat response", 1, TimeUnit.HOURS);
    }

    @Test
    @DisplayName("getCachedChat 应返回缓存的聊天")
    void shouldGetCachedChat() {
        when(valueOperations.get("ai:chat:hash-abc")).thenReturn("Cached chat");

        String result = cacheService.getCachedChat("hash-abc");

        assertThat(result).isEqualTo("Cached chat");
    }

    @Test
    @DisplayName("getCachedChat 缓存不存在时返回 null")
    void shouldReturnNullWhenChatNotCached() {
        when(valueOperations.get("ai:chat:hash-abc")).thenReturn(null);

        String result = cacheService.getCachedChat("hash-abc");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("invalidateNoteCache 应删除笔记相关缓存")
    void shouldInvalidateNoteCache() {
        cacheService.invalidateNoteCache("note-123");

        verify(redisTemplate).delete("ai:summary:note-123");
        verify(redisTemplate).delete("ai:actions:note-123");
    }
}
