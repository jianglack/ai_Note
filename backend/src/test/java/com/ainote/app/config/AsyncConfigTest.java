package com.ainote.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

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
