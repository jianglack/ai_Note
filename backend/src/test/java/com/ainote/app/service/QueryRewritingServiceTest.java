package com.ainote.app.service;

import com.ainote.app.util.PromptLoader;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

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
        QueryRewritingService service = new QueryRewritingService(mock(PromptLoader.class));
        ReflectionTestUtils.setField(service, "maxVariants", 3);

        List<String> normalized = service.normalizeQueryVariants(
                "original",
                List.of("expanded-a", "original", " ", "expanded-b", "expanded-c"));

        assertThat(normalized).containsExactly("original", "expanded-a", "expanded-b");
    }
}
