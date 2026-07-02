package com.ainote.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ainote.app.entity.Note;
import com.ainote.app.entity.User;
import com.ainote.app.repository.NoteRepository;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RagEvaluationServiceTest {

    private EmbeddingStore<TextSegment> embeddingStore;
    private ResilientLlmService resilientLlmService;
    private NoteRepository noteRepository;
    private RagEvaluationService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        embeddingStore = mock(EmbeddingStore.class);
        resilientLlmService = mock(ResilientLlmService.class);
        noteRepository = mock(NoteRepository.class);
        service = new RagEvaluationService(embeddingStore, resilientLlmService, noteRepository);
    }

    @Test
    void evaluateCalculatesRecallMrrAndNdcgForUserOwnedResults() {
        Embedding embedding = Embedding.from(new float[] {0.1f, 0.2f});
        TextSegment segment1 = TextSegment.from("one", Metadata.from("noteId", "n1"));
        TextSegment segment2 = TextSegment.from("two", Metadata.from("noteId", "n2"));
        when(resilientLlmService.embed(any(TextSegment.class))).thenReturn(Response.from(embedding));
        when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(List.of(
                        new EmbeddingMatch<>(0.95, "e1", embedding, segment1),
                        new EmbeddingMatch<>(0.80, "e2", embedding, segment2))));
        when(noteRepository.findAllById(any()))
                .thenReturn(List.of(note("n1", "user-1"), note("n2", "user-1")));

        RagEvaluationService.EvalReport report = service.evaluate(
                List.of(new RagEvaluationService.EvalCase("spring", List.of("n1", "n3"))),
                2,
                0.5,
                "user-1");

        assertThat(report.totalCases).isEqualTo(1);
        assertThat(report.avgRecallAtK).isEqualTo(0.5);
        assertThat(report.avgMrr).isEqualTo(1.0);
        assertThat(report.avgNdcg).isBetween(0.0, 1.0);
        assertThat(report.details.get(0).retrievedIds).containsExactly("n1", "n2");
    }

    @Test
    void evaluateEmptyInputIsSafe() {
        RagEvaluationService.EvalReport report = service.evaluate(List.of(), 3, 0.5, "user-1");

        assertThat(report.totalCases).isZero();
        assertThat(report.avgRecallAtK).isZero();
        assertThat(report.avgMrr).isZero();
        assertThat(report.avgNdcg).isZero();
        assertThat(report.details).isEmpty();
    }

    @Test
    void evaluateFallsBackToEmptyResultWhenEmbeddingUnavailable() {
        when(resilientLlmService.embed(any(TextSegment.class))).thenReturn(null);

        RagEvaluationService.EvalReport report = service.evaluate(
                List.of(new RagEvaluationService.EvalCase("query", List.of("n1"))),
                3,
                0.5,
                "user-1");

        assertThat(report.details.get(0).retrievedIds).isEmpty();
        assertThat(report.details.get(0).recallAtK).isZero();
    }

    @Test
    void evaluateFiltersResultsFromOtherUsers() {
        Embedding embedding = Embedding.from(new float[] {0.1f});
        TextSegment segment = TextSegment.from("one", Metadata.from("noteId", "n1"));
        when(resilientLlmService.embed(any(TextSegment.class))).thenReturn(Response.from(embedding));
        when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(List.of(new EmbeddingMatch<>(0.95, "e1", embedding, segment))));
        when(noteRepository.findAllById(any())).thenReturn(List.of(note("n1", "other-user")));

        RagEvaluationService.EvalReport report = service.evaluate(
                List.of(new RagEvaluationService.EvalCase("query", List.of("n1"))),
                3,
                0.5,
                "user-1");

        assertThat(report.details.get(0).retrievedIds).isEmpty();
        assertThat(report.avgRecallAtK).isZero();
    }

    private static Note note(String id, String userId) {
        User user = new User();
        user.setId(userId);
        Note note = new Note();
        note.setId(id);
        note.setTitle(id);
        note.setContent("content");
        note.setUser(user);
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());
        return note;
    }
}
