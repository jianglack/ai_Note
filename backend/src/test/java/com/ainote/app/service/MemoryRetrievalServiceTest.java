package com.ainote.app.service;

import com.ainote.app.entity.EpisodicMemory;
import com.ainote.app.entity.SemanticMemory;
import com.ainote.app.repository.EpisodicMemoryRepository;
import com.ainote.app.repository.SemanticMemoryRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class MemoryRetrievalServiceTest {

    private EmbeddingModel embeddingModel;
    private SemanticMemoryRepository semanticMemoryRepository;
    private EpisodicMemoryRepository episodicMemoryRepository;
    private MemoryMetricsService memoryMetricsService;
    private MemoryRetrievalService service;

    @BeforeEach
    void setUp() {
        embeddingModel = mock(EmbeddingModel.class);
        semanticMemoryRepository = mock(SemanticMemoryRepository.class);
        episodicMemoryRepository = mock(EpisodicMemoryRepository.class);
        memoryMetricsService = mock(MemoryMetricsService.class);
        service = new MemoryRetrievalService(
                embeddingModel,
                semanticMemoryRepository,
                episodicMemoryRepository,
                memoryMetricsService
        );
    }

    @Test
    void scoreFormulaWeightsSimilarityConfidenceRecencyReinforcementAndScope() {
        MemoryScore high = new MemoryScore(0.90, 0.90, 0.80, 0.40, 1.00);
        MemoryScore low = new MemoryScore(0.50, 0.70, 0.80, 0.40, 1.00);

        assertThat(high.total()).isCloseTo(0.845, within(0.001));
        assertThat(high.total()).isGreaterThan(low.total());
    }

    @Test
    void retrievesQueryRelevantSemanticAndEpisodicMemoriesByEmbedding() {
        when(embeddingModel.embed("summarize my architecture preference"))
                .thenReturn(Response.from(Embedding.from(new float[]{0.1f, 0.2f})));

        SemanticMemory relevant = semanticMemory(
                10L,
                "preference",
                "prefers compact architecture summaries",
                0.9,
                3,
                LocalDateTime.now().minusDays(1),
                "user"
        );
        SemanticMemory oldButLessRelevant = semanticMemory(
                11L,
                "fact",
                "likes weekly reports",
                0.95,
                10,
                LocalDateTime.now().minusDays(100),
                "user"
        );
        EpisodicMemory episode = episodicMemory(20L, "Discussed architecture memory retrieval.");

        when(semanticMemoryRepository.findRelevantSemanticMatches("user-1", "[0.1,0.2]", 0.25, 20))
                .thenReturn(List.<SemanticMemoryRepository.MemorySimilarityView>of(
                        new SemanticMemoryRepository.MemorySimilarity(10L, 0.92),
                        new SemanticMemoryRepository.MemorySimilarity(11L, 0.40)
                ));
        when(semanticMemoryRepository.findActiveByUserIdAndIdIn("user-1", List.of(10L, 11L)))
                .thenReturn(List.of(oldButLessRelevant, relevant));
        when(episodicMemoryRepository.findRelevantEpisodicMatches("user-1", "[0.1,0.2]", 0.25, 10))
                .thenReturn(List.<EpisodicMemoryRepository.MemorySimilarityView>of(
                        new EpisodicMemoryRepository.MemorySimilarity(20L, 0.87)
                ));
        when(episodicMemoryRepository.findActiveByUserIdAndIdIn("user-1", List.of(20L)))
                .thenReturn(List.of(episode));

        MemoryRetrievalService.MemoryRetrievalResult result = service.retrieveForQuery(
                "user-1",
                "summarize my architecture preference",
                2,
                1
        );

        assertThat(result.semanticMemories()).containsExactly(relevant, oldButLessRelevant);
        assertThat(result.episodicMemories()).containsExactly(episode);
        verify(semanticMemoryRepository).findRelevantSemanticMatches("user-1", "[0.1,0.2]", 0.25, 20);
        verify(episodicMemoryRepository).findRelevantEpisodicMatches("user-1", "[0.1,0.2]", 0.25, 10);
        verify(semanticMemoryRepository).markAccessed(eq("user-1"), eq(List.of(10L, 11L)), any(LocalDateTime.class));
        verify(memoryMetricsService).recordRetrieval(eq(2), eq(1), eq("query_relevant"), anyLong());
    }

    @Test
    void fallsBackToGovernedActiveMemoriesWhenEmbeddingFails() {
        when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException("embedding down"));
        SemanticMemory fallback = semanticMemory(
                30L,
                "preference",
                "prefers Chinese answers",
                0.8,
                2,
                LocalDateTime.now(),
                "user"
        );
        EpisodicMemory recent = episodicMemory(40L, "Recent user discussion.");

        when(semanticMemoryRepository.findRetrievalFallback(eq("user-1"), any(Pageable.class)))
                .thenReturn(List.of(fallback));
        when(episodicMemoryRepository.findRecentByUserId(eq("user-1"), any(Pageable.class)))
                .thenReturn(List.of(recent));

        MemoryRetrievalService.MemoryRetrievalResult result = service.retrieveForQuery(
                "user-1",
                "anything",
                5,
                3
        );

        assertThat(result.semanticMemories()).containsExactly(fallback);
        assertThat(result.episodicMemories()).containsExactly(recent);
        verify(semanticMemoryRepository).findRetrievalFallback(eq("user-1"), any(Pageable.class));
        verify(episodicMemoryRepository).findRecentByUserId(eq("user-1"), any(Pageable.class));
        verify(semanticMemoryRepository).markAccessed(eq("user-1"), eq(List.of(30L)), any(LocalDateTime.class));
    }

    @Test
    void blankQueryUsesFallbackWithoutCallingEmbeddingModel() {
        when(semanticMemoryRepository.findRetrievalFallback(eq("user-1"), any(Pageable.class)))
                .thenReturn(List.of());
        when(episodicMemoryRepository.findRecentByUserId(eq("user-1"), any(Pageable.class)))
                .thenReturn(List.of());

        MemoryRetrievalService.MemoryRetrievalResult result = service.retrieveForQuery(
                "user-1",
                " ",
                5,
                3
        );

        assertThat(result.semanticMemories()).isEmpty();
        assertThat(result.episodicMemories()).isEmpty();
        verifyNoMoreInteractions(embeddingModel);
        verify(memoryMetricsService).recordRetrieval(eq(0), eq(0), eq("fallback_blank_query"), anyLong());
    }

    private SemanticMemory semanticMemory(Long id,
                                          String category,
                                          String content,
                                          double confidence,
                                          int timesReinforced,
                                          LocalDateTime lastReinforcedAt,
                                          String scope) {
        SemanticMemory memory = new SemanticMemory();
        memory.setId(id);
        memory.setUserId("user-1");
        memory.setCategory(category);
        memory.setMemoryType(category);
        memory.setContent(content);
        memory.setConfidence(confidence);
        memory.setTimesReinforced(timesReinforced);
        memory.setLastReinforcedAt(lastReinforcedAt);
        memory.setScope(scope);
        memory.setStatus("active");
        memory.setDecayScore(confidence);
        return memory;
    }

    private EpisodicMemory episodicMemory(Long id, String summary) {
        EpisodicMemory memory = new EpisodicMemory();
        memory.setId(id);
        memory.setUserId("user-1");
        memory.setSessionSummary(summary);
        memory.setStatus("active");
        memory.setCreatedAt(LocalDateTime.now());
        return memory;
    }

    private org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
