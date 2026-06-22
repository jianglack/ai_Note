package com.ainote.app.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Agent 并发控制守卫。
 * <p>
 * 每个用户同时只能有一个 Agent 调用在执行。
 * 后续请求在短暂等待后返回友好提示，避免资源浪费和状态混乱。
 * </p>
 */
@Component
public class ConcurrencyGuard {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyGuard.class);

    private final ConcurrentHashMap<String, LockEntry> userLocks = new ConcurrentHashMap<>();

    private static final class LockEntry {
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicInteger references = new AtomicInteger();
    }

    /**
     * 尝试获取用户锁。
     *
     * @param userId    用户 ID
     * @param timeoutMs 等待超时时间（毫秒）
     * @return true 如果成功获取锁
     */
    public boolean tryAcquire(String userId, long timeoutMs) {
        LockEntry entry = userLocks.compute(userId, (key, existing) -> {
            LockEntry next = existing == null ? new LockEntry() : existing;
            next.references.incrementAndGet();
            return next;
        });
        try {
            boolean acquired = entry.lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                releaseReference(userId, entry);
                log.warn("ConcurrencyGuard: user {} already has an active agent call, rejecting", userId);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            releaseReference(userId, entry);
            log.warn("ConcurrencyGuard: interrupted while waiting for lock for user {}", userId);
            return false;
        }
    }

    /**
     * 释放用户锁。
     */
    public void release(String userId) {
        LockEntry entry = userLocks.get(userId);
        if (entry != null && entry.lock.isHeldByCurrentThread()) {
            entry.lock.unlock();
            releaseReference(userId, entry);
        }
    }

    private void releaseReference(String userId, LockEntry entry) {
        if (entry.references.decrementAndGet() == 0
                && !entry.lock.isLocked()
                && !entry.lock.hasQueuedThreads()) {
            userLocks.remove(userId, entry);
        }
    }
}
