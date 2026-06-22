package com.ainote.app.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

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
}
