package com.ainote.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResilientLlmServiceTest {

    private EmbeddingModel embeddingModel;
    private ScoringModel scoringModel;
    private ResilientLlmService service;

    @BeforeEach
    void setUp() {
        embeddingModel = mock(EmbeddingModel.class);
        scoringModel = mock(ScoringModel.class);
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .build();
        service = new ResilientLlmService(CircuitBreakerRegistry.of(config), embeddingModel, scoringModel);
    }

    @Test
    void embedReturnsModelResponseWhenAvailable() {
        Response<Embedding> response = Response.from(Embedding.from(new float[] {0.1f}));
        TextSegment segment = TextSegment.from("hello");
        when(embeddingModel.embed(segment)).thenReturn(response);

        assertThat(service.embed(segment)).isSameAs(response);
        assertThat(service.isEmbeddingAvailable()).isTrue();
    }

    @Test
    void embedReturnsNullAndOpensCircuitAfterFailures() {
        when(embeddingModel.embed(any(TextSegment.class))).thenThrow(new RuntimeException("embedding down"));

        assertThat(service.embed(TextSegment.from("a"))).isNull();
        assertThat(service.embed(TextSegment.from("b"))).isNull();

        assertThat(service.isEmbeddingAvailable()).isFalse();
    }

    @Test
    void embedAllFallsBackToEmptyList() {
        when(embeddingModel.embedAll(any())).thenThrow(new RuntimeException("batch down"));

        Response<List<Embedding>> response = service.embedAll(List.of(TextSegment.from("a")));

        assertThat(response.content()).isEmpty();
    }

    @Test
    void scoreAllReturnsScoresAndFallsBackToNull() {
        List<TextSegment> segments = List.of(TextSegment.from("a"));
        when(scoringModel.scoreAll(segments, "query")).thenReturn(Response.from(List.of(0.8)));

        assertThat(service.scoreAll(segments, "query").content()).containsExactly(0.8);

        when(scoringModel.scoreAll(any(), anyString())).thenThrow(new RuntimeException("rerank down"));
        assertThat(service.scoreAll(segments, "query")).isNull();
    }

    @Test
    void rerankUnavailableWhenScoringModelIsMissing() {
        ResilientLlmService withoutRerank = new ResilientLlmService(CircuitBreakerRegistry.ofDefaults(), embeddingModel, null);

        assertThat(withoutRerank.scoreAll(List.of(TextSegment.from("a")), "query")).isNull();
        assertThat(withoutRerank.isRerankAvailable()).isFalse();
    }
}
