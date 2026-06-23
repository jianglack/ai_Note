package com.ainote.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableScheduling
@EnableAsync
public class AsyncConfig {

    private static final int SECURITY_EXECUTOR_POOL_SIZE = 8;
    private static final int SECURITY_EXECUTOR_QUEUE_CAPACITY = 200;

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
                SECURITY_EXECUTOR_POOL_SIZE,
                SECURITY_EXECUTOR_POOL_SIZE,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(SECURITY_EXECUTOR_QUEUE_CAPACITY),
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
}
