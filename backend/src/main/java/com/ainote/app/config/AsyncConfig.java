package com.ainote.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableScheduling
@EnableAsync
public class AsyncConfig {

    private final int securityExecutorPoolSize;
    private final int securityExecutorQueueCapacity;
    private final int sseExecutorPoolSize;
    private final int sseExecutorQueueCapacity;
    private final int sseExecutorKeepAliveSeconds;

    public AsyncConfig() {
        this(8, 200, 128, 512, 30);
    }

    public AsyncConfig(
            @Value("${app.async.security.pool-size:8}") int securityExecutorPoolSize,
            @Value("${app.async.security.queue-capacity:200}") int securityExecutorQueueCapacity,
            @Value("${app.async.sse.pool-size:128}") int sseExecutorPoolSize,
            @Value("${app.async.sse.queue-capacity:512}") int sseExecutorQueueCapacity,
            @Value("${app.async.sse.keep-alive-seconds:30}") int sseExecutorKeepAliveSeconds) {
        this.securityExecutorPoolSize = Math.max(1, securityExecutorPoolSize);
        this.securityExecutorQueueCapacity = Math.max(1, securityExecutorQueueCapacity);
        this.sseExecutorPoolSize = Math.max(1, sseExecutorPoolSize);
        this.sseExecutorQueueCapacity = Math.max(1, sseExecutorQueueCapacity);
        this.sseExecutorKeepAliveSeconds = Math.max(1, sseExecutorKeepAliveSeconds);
    }

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-embedding-");
        executor.initialize();
        return executor;
    }

    /**
     * SecurityContext 自动传播的 ExecutorService
     * 用于 Agent SSE 流式调用和 CompletableFuture 异步调用
     * 所有提交的任务自动继承提交线程的 SecurityContext，无需手动 set/clear
     */
    @Bean(name = "securityExecutor")
    public ExecutorService securityExecutor() {
        ThreadPoolExecutor delegate = new ThreadPoolExecutor(
                securityExecutorPoolSize,
                securityExecutorPoolSize,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(securityExecutorQueueCapacity),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("agent-exec-" + t.getId());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
        return new DelegatingSecurityContextExecutorService(
                delegate
        );
    }

    @Bean(name = "sseExecutor")
    public ExecutorService sseExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                sseExecutorPoolSize,
                sseExecutorPoolSize,
                sseExecutorKeepAliveSeconds,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(sseExecutorQueueCapacity),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("sse-stream-" + t.getId());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    @Bean(name = "sseHeartbeatScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService sseHeartbeatScheduler() {
        return Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setName("sse-heartbeat-" + t.getId());
            t.setDaemon(true);
            return t;
        });
    }
}
