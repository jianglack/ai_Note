package com.ainote.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ainote.app.config.MemoryProperties;
import com.ainote.app.entity.SemanticMemory;
import com.ainote.app.repository.EpisodicMemoryRepository;
import com.ainote.app.repository.SemanticMemoryRepository;
import com.ainote.app.repository.UserMemoryRepository;
import com.ainote.app.util.PromptLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

class MemoryExtractionServiceTest {

    private ChatModel chatModel;
    private EmbeddingModel embeddingModel;
    private SemanticMemoryRepository semanticMemoryRepository;
    private EpisodicMemoryRepository episodicMemoryRepository;
    private UserMemoryRepository userMemoryRepository;
    private PromptLoader promptLoader;
    private MemoryProperties memoryProperties;
    private MemoryExtractionService service;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        embeddingModel = mock(EmbeddingModel.class);
        semanticMemoryRepository = mock(SemanticMemoryRepository.class);
        episodicMemoryRepository = mock(EpisodicMemoryRepository.class);
        userMemoryRepository = mock(UserMemoryRepository.class);
        promptLoader = mock(PromptLoader.class);
        memoryProperties = new MemoryProperties();
        service = new MemoryExtractionService(chatModel, embeddingModel, semanticMemoryRepository,
                episodicMemoryRepository, userMemoryRepository, promptLoader, new ObjectMapper(), memoryProperties);

        when(promptLoader.load("semantic-extraction.txt")).thenReturn("extract memories");
        when(embeddingModel.embed(any(String.class)))
                .thenReturn(Response.from(Embedding.from(new float[] {0.1f, 0.2f})));
        when(semanticMemoryRepository.findSimilarByEmbedding(eq("user-1"), any(String.class), anyDouble(), eq(3)))
                .thenReturn(List.of());
        when(semanticMemoryRepository.countByUserId("user-1")).thenReturn(1L);
        when(semanticMemoryRepository.save(any(SemanticMemory.class))).thenAnswer(invocation -> {
            SemanticMemory memory = invocation.getArgument(0);
            if (memory.getId() == null) {
                memory.setId(42L);
            }
            return memory;
        });
    }

    @Test
    void extractSemanticMemorySavesExtractedMemory() {
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(chatResponse("""
                        [
                          {"category":"preference","content":"likes markdown","confidence":0.9}
                        ]
                        """));

        service.extractSemanticMemoryAsync("user-1", "I like markdown", "noted");

        ArgumentCaptor<SemanticMemory> captor = ArgumentCaptor.forClass(SemanticMemory.class);
        verify(semanticMemoryRepository).save(captor.capture());
        SemanticMemory saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getCategory()).isEqualTo("preference");
        assertThat(saved.getContent()).isEqualTo("likes markdown");
        assertThat(saved.getConfidence()).isEqualTo(0.9);
        assertThat(saved.getEmbedding()).containsExactly(0.1f, 0.2f);
        verify(semanticMemoryRepository).updateEmbedding(42L, "[0.1,0.2]");
    }

    @Test
    void extractSemanticMemoryReinforcesSimilarExistingMemory() {
        SemanticMemory existing = new SemanticMemory();
        existing.setUserId("user-1");
        existing.setCategory("preference");
        existing.setContent("likes markdown");
        existing.setConfidence(0.6);
        existing.setTimesReinforced(1);
        when(semanticMemoryRepository.findSimilarByEmbedding(eq("user-1"), any(String.class), anyDouble(), eq(3)))
                .thenReturn(List.of(existing));
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(chatResponse("[{\"category\":\"preference\",\"content\":\"likes markdown\",\"confidence\":0.9}]"));

        service.extractSemanticMemoryAsync("user-1", "I still like markdown", "noted");

        verify(semanticMemoryRepository).save(existing);
        assertThat(existing.getTimesReinforced()).isEqualTo(2);
        assertThat(existing.getConfidence()).isEqualTo(0.9);
    }

    @Test
    void extractSemanticMemoryFallsBackWithoutSavingWhenLlmFails() {
        when(chatModel.chat(any(ChatRequest.class))).thenThrow(new IllegalStateException("llm down"));

        service.extractSemanticMemoryAsync("user-1", "hello", "hi");

        verify(semanticMemoryRepository, never()).save(any(SemanticMemory.class));
    }

    @Test
    void refreshDecayScoresUsesConfiguredHalfLife() {
        memoryProperties.setDecayHalfLifeDays(10.0);
        SemanticMemory memory = new SemanticMemory();
        memory.setUserId("user-1");
        memory.setCategory("preference");
        memory.setContent("likes detailed plans");
        memory.setConfidence(1.0);
        memory.setTimesReinforced(1);
        memory.setLastReinforcedAt(LocalDateTime.now().minusDays(30).minusMinutes(1));
        when(semanticMemoryRepository.findByUserId("user-1")).thenReturn(List.of(memory));

        service.refreshDecayScores("user-1");

        assertThat(memory.getDecayScore()).isEqualTo(0.25);
        verify(semanticMemoryRepository).saveAll(List.of(memory));
    }

    @Test
    void extractSemanticMemoryWritesStructuredAuditLogWhenEnabled() {
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(chatResponse("""
                        [
                          {"category":"preference","content":"likes markdown","confidence":0.9}
                        ]
                        """));
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(MemoryExtractionService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            service.extractSemanticMemoryAsync("user-1", "I like markdown", "noted");

            assertThat(appender.list)
                    .anySatisfy(event -> assertThat(event.getFormattedMessage())
                            .contains("memory_event=semantic_extraction")
                            .contains("user_id=user-1")
                            .contains("audit_enabled=true")
                            .contains("candidate_count=1")
                            .contains("saved_count=1")
                            .contains("reinforced_count=0")
                            .contains("conflict_updated_count=0"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    private static ChatResponse chatResponse(String text) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text.trim()))
                .build();
    }
}
