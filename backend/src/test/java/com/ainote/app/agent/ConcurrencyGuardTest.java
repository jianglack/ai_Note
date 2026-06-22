package com.ainote.app.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("ConcurrencyGuard 并发控制测试")
class ConcurrencyGuardTest {

    private ConcurrencyGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ConcurrencyGuard();
    }

    @Test
    @DisplayName("首次获取应成功")
    void shouldAcquireOnFirstAttempt() {
        boolean acquired = guard.tryAcquire("user1", 100);
        assertThat(acquired).isTrue();
        guard.release("user1");
    }

    @Test
    @DisplayName("释放后应能再次获取")
    void shouldAcquireAfterRelease() {
        guard.tryAcquire("user1", 100);
        guard.release("user1");

        boolean acquired = guard.tryAcquire("user1", 100);
        assertThat(acquired).isTrue();
        guard.release("user1");
    }

    @Test
    void shouldRemoveIdleLockAfterRelease() {
        guard.tryAcquire("user1", 100);
        guard.release("user1");

        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, ?> userLocks =
                (ConcurrentHashMap<String, ?>) ReflectionTestUtils.getField(guard, "userLocks");

        assertThat(userLocks).isEmpty();
    }

    @Test
    @DisplayName("不同用户应互不影响")
    void shouldAllowDifferentUsers() {
        boolean a1 = guard.tryAcquire("user1", 100);
        boolean a2 = guard.tryAcquire("user2", 100);

        assertThat(a1).isTrue();
        assertThat(a2).isTrue();

        guard.release("user1");
        guard.release("user2");
    }

    @Test
    @DisplayName("同一用户并发请求应被拒绝")
    void shouldRejectConcurrentSameUser() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean secondResult = new AtomicBoolean(true);

        // 线程1：获取锁并持有
        executor.submit(() -> {
            guard.tryAcquire("user1", 100);
            acquired.countDown();
            try {
                done.await(); // 持有锁直到测试完成
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                guard.release("user1");
            }
        });

        acquired.await(); // 等待线程1获取锁

        // 线程2：尝试获取同一用户的锁（应被拒绝）
        executor.submit(() -> {
            secondResult.set(guard.tryAcquire("user1", 50));
            if (secondResult.get()) {
                guard.release("user1");
            }
        }).get(); // 等待线程2完成

        assertThat(secondResult.get()).isFalse();

        done.countDown();
        executor.shutdown();
    }
}
