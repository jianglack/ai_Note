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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryWriteServiceTest {

    private SemanticMemoryRepository semanticMemoryRepository;
    private MemoryEventRepository memoryEventRepository;
    private EmbeddingModel embeddingModel;
    private MemoryMetricsService memoryMetricsService;
    private MemoryWriteService service;

    @BeforeEach
    void setUp() {
        semanticMemoryRepository = mock(SemanticMemoryRepository.class);
        memoryEventRepository = mock(MemoryEventRepository.class);
        embeddingModel = mock(EmbeddingModel.class);
        memoryMetricsService = mock(MemoryMetricsService.class);
        service = new MemoryWriteService(
                semanticMemoryRepository,
                memoryEventRepository,
                embeddingModel,
                new MemoryPrivacyService(),
                memoryMetricsService);

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
        assertThat(saved.getContentHash()).matches("sha256:[a-f0-9]{64}");
        assertThat(saved.getSourceTraceId()).matches("memory-capture-[a-f0-9]{24}");
        assertThat(saved.getSourceMessageIds()).contains("user_message_hash", "sha256:");
        assertThat(saved.getMetadataJson()).contains("capture_reason", "explicit");
        assertThat(saved.getEvidenceExcerpt()).contains("记住");
        verify(semanticMemoryRepository).updateEmbedding(100L, "[0.1,0.2]");

        ArgumentCaptor<MemoryEvent> eventCaptor = ArgumentCaptor.forClass(MemoryEvent.class);
        verify(memoryEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("CREATED");
        assertThat(eventCaptor.getValue().getActor()).isEqualTo("assistant");
        assertThat(eventCaptor.getValue().getTraceId()).isEqualTo(saved.getSourceTraceId());
        assertThat(eventCaptor.getValue().getAfterJson()).contains("contentHash");
        verify(memoryMetricsService).recordWriteResult(result, "explicit");
    }

    @Test
    void skipsSensitiveCandidateBeforePersistingMemory() {
        MemoryCandidateExtractor.MemoryCandidate candidate = new MemoryCandidateExtractor.MemoryCandidate(
                "fact",
                "fact",
                "backup email alice@example.com",
                0.95,
                "user",
                "remember my backup email is alice@example.com",
                false);

        MemoryWriteService.MemoryWriteResult result = service.writeCandidates(
                "user-1", List.of(candidate), "explicit");

        assertThat(result.skipped()).isEqualTo(1);
        verify(semanticMemoryRepository, never()).save(any(SemanticMemory.class));
        verify(semanticMemoryRepository, never()).updateEmbedding(any(Long.class), any(String.class));

        ArgumentCaptor<MemoryEvent> eventCaptor = ArgumentCaptor.forClass(MemoryEvent.class);
        verify(memoryEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("CAPTURE_REJECTED_PRIVACY");
        assertThat(eventCaptor.getValue().getAfterJson()).contains("\"email\":1");
        assertThat(eventCaptor.getValue().getAfterJson()).doesNotContain("alice@example.com");
        verify(memoryMetricsService).recordWriteResult(result, "explicit");
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
        assertThat(eventCaptor.getAllValues())
                .allSatisfy(event -> assertThat(event.getTraceId()).matches("memory-capture-[a-f0-9]{24}"));
    }

    @Test
    void reinforcesExactPreferenceWithProvenanceAndEventTrace() {
        SemanticMemory existing = new SemanticMemory();
        existing.setId(42L);
        existing.setUserId("user-1");
        existing.setCategory("preference");
        existing.setMemoryType("preference");
        existing.setContent("prefers Chinese replies");
        existing.setStatus("active");
        existing.setTimesReinforced(1);

        MemoryCandidateExtractor.MemoryCandidate candidate = new MemoryCandidateExtractor.MemoryCandidate(
                "preference",
                "preference",
                "prefers Chinese replies",
                0.95,
                "user",
                "璁颁綇锛屾垜甯屾湜浣犵敤涓枃鍥炵瓟",
                false);
        when(semanticMemoryRepository.findByUserIdAndContent("user-1", "prefers Chinese replies"))
                .thenReturn(List.of(existing));

        MemoryWriteService.MemoryWriteResult result = service.writeCandidates("user-1", List.of(candidate), "explicit");

        assertThat(result.reinforced()).isEqualTo(1);
        assertThat(existing.getTimesReinforced()).isEqualTo(2);
        assertThat(existing.getContentHash()).matches("sha256:[a-f0-9]{64}");
        assertThat(existing.getSourceTraceId()).matches("memory-capture-[a-f0-9]{24}");

        ArgumentCaptor<MemoryEvent> eventCaptor = ArgumentCaptor.forClass(MemoryEvent.class);
        verify(memoryEventRepository).save(eventCaptor.capture());
        MemoryEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("REINFORCED");
        assertThat(event.getTraceId()).isEqualTo(existing.getSourceTraceId());
    }

    @Test
    void recordsCaptureFailureAsLedgerEvent() {
        service.recordCaptureFailure(
                "user-1",
                "capture_exception",
                new IllegalStateException("extractor down"),
                "memory-capture-deadbeefdeadbeefdeadbeef");

        ArgumentCaptor<MemoryEvent> eventCaptor = ArgumentCaptor.forClass(MemoryEvent.class);
        verify(memoryEventRepository).save(eventCaptor.capture());
        MemoryEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("CAPTURE_FAILED");
        assertThat(event.getActor()).isEqualTo("system");
        assertThat(event.getReason()).isEqualTo("capture_exception");
        assertThat(event.getTraceId()).isEqualTo("memory-capture-deadbeefdeadbeefdeadbeef");
        assertThat(event.getAfterJson()).contains("IllegalStateException", "extractor down");
    }

    @Test
    void recordsDeniedCaptureDecisionWithRedactedEvidenceAndSignals() {
        MemoryCapturePolicy.CaptureDecision decision = new MemoryCapturePolicy.CaptureDecision(
                MemoryCapturePolicy.DecisionType.DENY_SENSITIVE,
                false,
                "sensitive_content",
                0.0,
                List.of("sensitive_content"));

        service.recordCaptureDecision(
                "user-1",
                "denied",
                "sensitive_content",
                decision,
                "请记住我的手机号：13812345678");

        ArgumentCaptor<MemoryEvent> eventCaptor = ArgumentCaptor.forClass(MemoryEvent.class);
        verify(memoryEventRepository).save(eventCaptor.capture());
        MemoryEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("CAPTURE_REJECTED");
        assertThat(event.getActor()).isEqualTo("system");
        assertThat(event.getAfterJson()).contains("\"matchedSignals\":[\"sensitive_content\"]");
        assertThat(event.getAfterJson()).contains("\"phone\":1");
        assertThat(event.getAfterJson()).doesNotContain("13812345678");
        assertThat(event.getTraceId()).matches("memory-capture-[a-f0-9]{24}");
    }

    @Test
    void writesPolicyExplanationIntoMetadataAndEventSnapshot() {
        MemoryCandidateExtractor.MemoryCandidate candidate = new MemoryCandidateExtractor.MemoryCandidate(
                "style",
                "preference",
                "希望交互风格严肃一些，少开玩笑",
                0.75,
                "user",
                "以后回答请严肃一些，少开玩笑。",
                false,
                List.of("stable_style_preference", "preference_signal"),
                MemoryCapturePolicy.DecisionType.ALLOW_IMPLICIT_LOW_CONFIDENCE.name(),
                "implicit_interaction_style");
        when(semanticMemoryRepository.findByUserIdAndContent("user-1", "希望交互风格严肃一些，少开玩笑"))
                .thenReturn(List.of());

        service.writeCandidates("user-1", List.of(candidate), "implicit_interaction_style");

        ArgumentCaptor<SemanticMemory> memoryCaptor = ArgumentCaptor.forClass(SemanticMemory.class);
        verify(semanticMemoryRepository).save(memoryCaptor.capture());
        SemanticMemory saved = memoryCaptor.getValue();
        assertThat(saved.getMetadataJson())
                .contains("\"decision_type\":\"ALLOW_IMPLICIT_LOW_CONFIDENCE\"")
                .contains("\"policy_reason\":\"implicit_interaction_style\"")
                .contains("\"policy_signals\":[\"stable_style_preference\",\"preference_signal\"]");

        ArgumentCaptor<MemoryEvent> eventCaptor = ArgumentCaptor.forClass(MemoryEvent.class);
        verify(memoryEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getAfterJson())
                .contains("\"metadata\"")
                .contains("\"policy_signals\":[\"stable_style_preference\",\"preference_signal\"]");
    }
}
