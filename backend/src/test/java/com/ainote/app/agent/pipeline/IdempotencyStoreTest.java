package com.ainote.app.agent.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IdempotencyStore 幂等缓存测试")
class IdempotencyStoreTest {

    private IdempotencyStore store;

    @BeforeEach
    void setUp() {
        store = new IdempotencyStore();
    }

    @Test
    @DisplayName("首次操作不应被判为重复")
    void shouldNotBeDuplicateOnFirstCall() {
        boolean dup = store.isDuplicate("user1:noteAction:create:abc123");
        assertThat(dup).isFalse();
    }

    @Test
    @DisplayName("相同 key 第二次应被判为重复")
    void shouldBeDuplicateOnSecondCall() {
        String key = "user1:noteAction:create:abc123";
        store.isDuplicate(key); // 第一次
        boolean dup = store.isDuplicate(key); // 第二次
        assertThat(dup).isTrue();
    }

    @Test
    @DisplayName("不同 key 不应互相影响")
    void shouldNotAffectDifferentKeys() {
        store.isDuplicate("user1:noteAction:create:abc");
        boolean dup = store.isDuplicate("user1:noteAction:create:def");
        assertThat(dup).isFalse();
    }

    @Test
    @DisplayName("invalidate 后应允许重新执行")
    void shouldAllowAfterInvalidate() {
        String key = "user1:noteAction:create:abc123";
        store.isDuplicate(key);
        store.invalidate(key);
        boolean dup = store.isDuplicate(key);
        assertThat(dup).isFalse();
    }

    @Test
    @DisplayName("clearByPrefix 应清除匹配前缀的所有条目")
    void shouldClearByPrefix() {
        store.isDuplicate("user1:noteAction:create:a");
        store.isDuplicate("user1:noteAction:delete:b");
        store.isDuplicate("user2:noteAction:create:c");

        store.clearByPrefix("user1:");

        // user1 的条目已清除，应不再重复
        assertThat(store.isDuplicate("user1:noteAction:create:a")).isFalse();
        assertThat(store.isDuplicate("user1:noteAction:delete:b")).isFalse();
        // user2 的条目不受影响
        assertThat(store.isDuplicate("user2:noteAction:create:c")).isTrue();
    }
}
