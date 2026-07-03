package com.ainote.app.service;

import com.ainote.app.entity.MemoryEvent;
import com.ainote.app.entity.SemanticMemory;
import com.ainote.app.repository.MemoryEventRepository;
import com.ainote.app.repository.SemanticMemoryRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryWriteServiceTest {

    private SemanticMemoryRepository semanticMemoryRepository;
    private MemoryEventRepository memoryEventRepository;
    private EmbeddingModel embeddingModel;
    private MemoryWriteService service;

    @BeforeEach
    void setUp() {
        semanticMemoryRepository = mock(SemanticMemoryRepository.class);
        memoryEventRepository = mock(MemoryEventRepository.class);
        embeddingModel = mock(EmbeddingModel.class);
        service = new MemoryWriteService(semanticMemoryRepository, memoryEventRepository, embeddingModel);

        when(embeddingModel.embed(any(String.class)))
                .thenReturn(Response.from(Embedding.from(new float[]{0.1f, 0.2f})));
        when(semanticMemoryRepository.save(any(SemanticMemory.class))).thenAnswer(invocation -> {
            SemanticMemory memory = invocation.getArgument(0);
            if (memory.getId() == null) {
                memory.setId(100L);
            }
            return memory;
        });
    }

    @Test
    void writesNewExplicitPreferenceWithProvenanceAndEvent() {
        MemoryCandidateExtractor.MemoryCandidate candidate = new MemoryCandidateExtractor.MemoryCandidate(
                "preference",
                "preference",
                "prefers Chinese replies",
                0.95,
                "user",
                "记住，我希望你用中文回答",
                false);
        when(semanticMemoryRepository.findByUserIdAndContent("user-1", "prefers Chinese replies"))
                .thenReturn(List.of());

        MemoryWriteService.MemoryWriteResult result = service.writeCandidates("user-1", List.of(candidate), "explicit");

        assertThat(result.created()).isEqualTo(1);
        ArgumentCaptor<SemanticMemory> memoryCaptor = ArgumentCaptor.forClass(SemanticMemory.class);
        verify(semanticMemoryRepository).save(memoryCaptor.capture());
        SemanticMemory saved = memoryCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo("active");
        assertThat(saved.getMemoryType()).isEqualTo("preference");
        assertThat(saved.getSource()).isEqualTo("policy_extracted");
        assertThat(saved.getEvidenceExcerpt()).contains("记住");
        verify(semanticMemoryRepository).updateEmbedding(100L, "[0.1,0.2]");

        ArgumentCaptor<MemoryEvent> eventCaptor = ArgumentCaptor.forClass(MemoryEvent.class);
        verify(memoryEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("CREATED");
        assertThat(eventCaptor.getValue().getActor()).isEqualTo("assistant");
    }

    @Test
    void correctionSupersedesOldPreferenceAndCreatesReplacement() {
        SemanticMemory old = new SemanticMemory();
        old.setId(41L);
        old.setUserId("user-1");
        old.setCategory("preference");
        old.setMemoryType("preference");
        old.setContent("prefers English replies");
        old.setStatus("active");

        MemoryCandidateExtractor.MemoryCandidate candidate = new MemoryCandidateExtractor.MemoryCandidate(
                "preference",
                "preference",
                "prefers Chinese replies",
                0.95,
                "user",
                "不再希望你用英文回复，请改为用中文回复",
                true);
        when(semanticMemoryRepository.findByUserIdAndContent("user-1", "prefers Chinese replies"))
                .thenReturn(List.of());
        when(semanticMemoryRepository.findByUserIdAndCategory("user-1", "preference"))
                .thenReturn(List.of(old));

        MemoryWriteService.MemoryWriteResult result = service.writeCandidates("user-1", List.of(candidate), "correction");

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.superseded()).isEqualTo(1);
        assertThat(old.getStatus()).isEqualTo("superseded");

        ArgumentCaptor<SemanticMemory> memoryCaptor = ArgumentCaptor.forClass(SemanticMemory.class);
        verify(semanticMemoryRepository, org.mockito.Mockito.times(2)).save(memoryCaptor.capture());
        SemanticMemory replacement = memoryCaptor.getAllValues().stream()
                .filter(memory -> "prefers Chinese replies".equals(memory.getContent()))
                .findFirst()
                .orElseThrow();
        assertThat(replacement.getSupersedesId()).isEqualTo(41L);

        ArgumentCaptor<MemoryEvent> eventCaptor = ArgumentCaptor.forClass(MemoryEvent.class);
        verify(memoryEventRepository, org.mockito.Mockito.atLeast(2)).save(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(MemoryEvent::getEventType)
                .contains("SUPERSEDED", "CREATED");
    }
}
