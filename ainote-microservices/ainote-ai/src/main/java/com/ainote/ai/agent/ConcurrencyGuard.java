package com.ainote.ai.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class ConcurrencyGuard {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyGuard.class);

    private final ConcurrentHashMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    public boolean tryAcquire(String userId, long timeoutMs) {
        ReentrantLock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
        try {
            boolean acquired = lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.warn("ConcurrencyGuard: user {} already has an active agent call, rejecting", userId);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void release(String userId) {
        ReentrantLock lock = userLocks.get(userId);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
