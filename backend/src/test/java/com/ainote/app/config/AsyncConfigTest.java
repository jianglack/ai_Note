package com.ainote.app.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void securityExecutorUsesBoundedThreadPool() throws Exception {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "ainote", "app", "config", "AsyncConfig.java"));

        assertThat(source)
                .doesNotContain("newCachedThreadPool")
                .contains("ThreadPoolExecutor")
                .contains("ArrayBlockingQueue");
    }

    @Test
    void sseExecutorUsesDedicatedBoundedPool() throws Exception {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "ainote", "app", "config", "AsyncConfig.java"));

        assertThat(source)
                .contains("@Bean(name = \"sseExecutor\")")
                .contains("sseExecutorPoolSize")
                .contains("sseExecutorQueueCapacity")
                .contains("sse-stream-");
    }

    @Test
    void sseExecutorAllowsIdleCoreThreadsToTimeOut() {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) new AsyncConfig().sseExecutor();

        try {
            assertThat(executor.allowsCoreThreadTimeOut()).isTrue();
            assertThat(executor.getKeepAliveTime(TimeUnit.SECONDS)).isLessThanOrEqualTo(30);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void securityExecutorPropagatesSecurityContextFromSubmittingThread() throws Exception {
        AsyncConfig config = new AsyncConfig(1, 10, 1, 10, 30);
        ExecutorService executor = config.securityExecutor();
        Authentication authentication =
                new UsernamePasswordAuthenticationToken("alice", "password", List.of());

        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);

            Authentication observed = executor.submit(
                    () -> SecurityContextHolder.getContext().getAuthentication()
            ).get(5, TimeUnit.SECONDS);

            assertThat(observed).isSameAs(authentication);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sseExecutorDoesNotPropagateSecurityContextByItself() throws Exception {
        AsyncConfig config = new AsyncConfig(1, 10, 1, 10, 30);
        ExecutorService executor = config.sseExecutor();
        Authentication authentication =
                new UsernamePasswordAuthenticationToken("alice", "password", List.of());
        CompletableFuture<Authentication> observed = new CompletableFuture<>();

        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            executor.execute(() -> observed.complete(
                    SecurityContextHolder.getContext().getAuthentication()));

            assertThat(observed.get(5, TimeUnit.SECONDS)).isNull();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void executorPoolSizesCanBeConfiguredForCapacityTests() throws Exception {
        AsyncConfig config = new AsyncConfig(64, 256, 96, 384, 20);
        ExecutorService securityExecutor = config.securityExecutor();
        ThreadPoolExecutor securityDelegate = unwrapSecurityDelegate(securityExecutor);
        ThreadPoolExecutor sseExecutor =
                (ThreadPoolExecutor) config.sseExecutor();

        try {
            assertThat(securityExecutor).isInstanceOf(DelegatingSecurityContextExecutorService.class);
            assertThat(securityDelegate.getCorePoolSize()).isEqualTo(64);
            assertThat(securityDelegate.getQueue().remainingCapacity()).isEqualTo(256);
            assertThat(sseExecutor.getCorePoolSize()).isEqualTo(96);
            assertThat(sseExecutor.getQueue().remainingCapacity()).isEqualTo(384);
            assertThat(sseExecutor.getKeepAliveTime(TimeUnit.SECONDS)).isEqualTo(20);
        } finally {
            securityExecutor.shutdownNow();
            sseExecutor.shutdownNow();
        }
    }

    private static ThreadPoolExecutor unwrapSecurityDelegate(ExecutorService securityExecutor) throws Exception {
        Method getDelegate = DelegatingSecurityContextExecutorService.class.getDeclaredMethod("getDelegate");
        getDelegate.setAccessible(true);
        return (ThreadPoolExecutor) getDelegate.invoke(securityExecutor);
    }
}
