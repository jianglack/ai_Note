package com.ainote.app.service;

import com.ainote.app.util.PromptLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class QueryRewritingServiceTest {

    @Test
    void rewriteQuery_isCacheable() throws Exception {
        Cacheable cacheable = QueryRewritingService.class
                .getMethod("rewriteQuery", String.class)
                .getAnnotation(Cacheable.class);

        assertThat(cacheable).isNotNull();
        assertThat(cacheable.cacheNames()).contains("queryRewrite");
    }

    @Test
    void normalizeQueryVariants_keepsOriginalDeduplicatesAndLimitsToThree() {
        QueryRewritingService service = new QueryRewritingService(mock(PromptLoader.class), new ObjectMapper());
        ReflectionTestUtils.setField(service, "maxVariants", 3);

        List<String> normalized = service.normalizeQueryVariants(
                "original",
                List.of("expanded-a", "original", " ", "expanded-b", "expanded-c"));

        assertThat(normalized).containsExactly("original", "expanded-a", "expanded-b");
    }

    @Test
    void rewriteQuery_skipsExternalCallForStructuredKeywords() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();

        try {
            QueryRewritingService service = new QueryRewritingService(mock(PromptLoader.class), new ObjectMapper());
            ReflectionTestUtils.setField(service, "enabled", true);
            ReflectionTestUtils.setField(service, "apiKey", "test-key");
            ReflectionTestUtils.setField(service, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
            ReflectionTestUtils.setField(service, "maxVariants", 3);
            ReflectionTestUtils.setField(service, "skipStructuredKeywords", true);

            List<String> rewritten = service.rewriteQuery("planning-123-1-0");

            assertThat(rewritten).containsExactly("planning-123-1-0");
            assertThat(requestCount).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rewriteQuery_usesConfiguredMaxTokensForNaturalLanguage() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                    {"choices":[{"message":{"content":"expanded project plan\\nrelease checklist"}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            QueryRewritingService service = new QueryRewritingService(mock(PromptLoader.class), new ObjectMapper());
            ReflectionTestUtils.setField(service, "enabled", true);
            ReflectionTestUtils.setField(service, "strategy", "expand");
            ReflectionTestUtils.setField(service, "apiKey", "test-key");
            ReflectionTestUtils.setField(service, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
            ReflectionTestUtils.setField(service, "maxVariants", 3);
            ReflectionTestUtils.setField(service, "maxTokens", 77);
            ReflectionTestUtils.setField(service, "timeoutSeconds", 2);

            List<String> rewritten = service.rewriteQuery("how should I plan this release");

            assertThat(requestBody.get()).contains("\"max_tokens\":77");
            assertThat(rewritten).containsExactly(
                    "how should I plan this release",
                    "expanded project plan",
                    "release checklist");
        } finally {
            server.stop(0);
        }
    }
}
