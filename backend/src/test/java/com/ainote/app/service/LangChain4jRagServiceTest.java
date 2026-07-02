package com.ainote.app.service;

import com.ainote.app.entity.Note;
import com.ainote.app.entity.User;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.security.SecurityUtils;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
@DisplayName("LangChain4jRagService unit tests")
class LangChain4jRagServiceTest {
    private EmbeddingModel embeddingModel;
    private EmbeddingStore<TextSegment> embeddingStore;
    private NoteRepository noteRepository;
    private SecurityUtils securityUtils;
    private QueryRewritingService queryRewritingService;
    private JdbcTemplate jdbcTemplate;
    private com.ainote.app.chunking.StructureAwareDocumentSplitter structureAwareSplitter;
    private ResilientLlmService resilientLlmService;
    private com.ainote.app.repository.NoteMediaRepository noteMediaRepository;
    private LangChain4jRagService ragService;

    private User testUser;
    private Note testNote;

    @BeforeEach
    void setUp() {
        embeddingModel = mock(EmbeddingModel.class);
        embeddingStore = mock(EmbeddingStore.class);
        noteRepository = mock(NoteRepository.class);
        securityUtils = mock(SecurityUtils.class);
        queryRewritingService = mock(QueryRewritingService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        structureAwareSplitter = mock(com.ainote.app.chunking.StructureAwareDocumentSplitter.class);
        resilientLlmService = mock(ResilientLlmService.class);
        noteMediaRepository = mock(com.ainote.app.repository.NoteMediaRepository.class);
        ragService = new LangChain4jRagService(
                embeddingModel, embeddingStore,
                noteRepository, securityUtils, null,
                queryRewritingService, jdbcTemplate, structureAwareSplitter,
                resilientLlmService, noteMediaRepository,
                10, 0.5
        );

        testUser = new User();
        testUser.setId("user-123");

        testNote = new Note();
        testNote.setId("note-456");
        testNote.setTitle("test note");
        testNote.setContent("<p>test content</p>");
        testNote.setUser(testUser);
        testNote.setCreatedAt(LocalDateTime.now());
        testNote.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("searchSimilar should use Query Rewriting")
    void shouldUseQueryRewriting() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(queryRewritingService.rewriteQuery("test"))
                .thenReturn(List.of("test", "test expanded"));
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(Embedding.from(new float[]{0.1f})));
        when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(List.of()));

        ragService.searchSimilar("test", 10);

        verify(queryRewritingService).rewriteQuery("test");
        verify(embeddingStore, times(2)).search(any(EmbeddingSearchRequest.class));
    }

    @Test
    @DisplayName("searchSimilar should return distinct notes")
    void shouldReturnDistinctNotes() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(queryRewritingService.rewriteQuery(anyString()))
                .thenReturn(List.of("query1", "query2"));

        Embedding fakeEmbedding = Embedding.from(new float[]{0.1f});
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(fakeEmbedding));

        TextSegment segment = TextSegment.from("test content",
                Metadata.from("noteId", "note-456").put("userId", "user-123"));
        EmbeddingMatch<TextSegment> match =
                new EmbeddingMatch<>(0.9, "id", fakeEmbedding, segment);
        when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(List.of(match)));
        when(noteRepository.findByIdsAndUserIdAndDeletedAtIsNull(any(), eq("user-123")))
                .thenReturn(List.of(testNote));

        List<com.ainote.app.model.Note> result = ragService.searchSimilar("test", 10);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("searchSimilar should filter other users' notes")
    void shouldFilterOtherUsersNotes() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(queryRewritingService.rewriteQuery(anyString()))
                .thenReturn(List.of("query"));

        Embedding fakeEmbedding = Embedding.from(new float[]{0.1f});
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(fakeEmbedding));

        TextSegment segment = TextSegment.from("test content",
                Metadata.from("noteId", "note-789").put("userId", "other-user"));
        EmbeddingMatch<TextSegment> match =
                new EmbeddingMatch<>(0.9, "id", fakeEmbedding, segment);
        when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(List.of(match)));

        when(noteRepository.findByIdsAndUserIdAndDeletedAtIsNull(any(), eq("user-123")))
                .thenReturn(List.of());

        List<com.ainote.app.model.Note> result = ragService.searchSimilar("test", 10);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("searchSimilar should filter deleted notes")
    void shouldFilterDeletedNotes() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(queryRewritingService.rewriteQuery(anyString()))
                .thenReturn(List.of("query"));

        Embedding fakeEmbedding = Embedding.from(new float[]{0.1f});
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(fakeEmbedding));

        TextSegment segment = TextSegment.from("test content",
                Metadata.from("noteId", "note-456").put("userId", "user-123"));
        EmbeddingMatch<TextSegment> match =
                new EmbeddingMatch<>(0.9, "id", fakeEmbedding, segment);
        when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(List.of(match)));

        when(noteRepository.findByIdsAndUserIdAndDeletedAtIsNull(any(), eq("user-123")))
                .thenReturn(List.of());

        List<com.ainote.app.model.Note> result = ragService.searchSimilar("test", 10);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getRelevantContext should return text segments")
    void shouldReturnRelevantContext() {
        Embedding fakeEmbedding = Embedding.from(new float[]{0.1f});
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(fakeEmbedding));

        TextSegment segment1 = TextSegment.from("related content 1",
                Metadata.from("noteId", "1").put("userId", "user-123"));
        TextSegment segment2 = TextSegment.from("related content 2",
                Metadata.from("noteId", "2").put("userId", "user-123"));
        when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(List.of(
                        new EmbeddingMatch<>(0.9, "id1", fakeEmbedding, segment1),
                        new EmbeddingMatch<>(0.8, "id2", fakeEmbedding, segment2)
                )));

        List<String> result = ragService.getRelevantContext("test", 5, "user-123");

        assertThat(result).containsExactly("related content 1", "related content 2");
        verify(queryRewritingService, never()).rewriteQuery(anyString());
    }

    @Test
    @DisplayName("getRelevantContext should limit result count")
    void shouldLimitContextResults() {
        Embedding fakeEmbedding = Embedding.from(new float[]{0.1f});
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(fakeEmbedding));

        TextSegment segment1 = TextSegment.from("content 1",
                Metadata.from("noteId", "1").put("userId", "user-123"));
        TextSegment segment2 = TextSegment.from("content 2",
                Metadata.from("noteId", "2").put("userId", "user-123"));
        TextSegment segment3 = TextSegment.from("content 3",
                Metadata.from("noteId", "3").put("userId", "user-123"));
        when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(List.of(
                        new EmbeddingMatch<>(0.9, "id1", fakeEmbedding, segment1),
                        new EmbeddingMatch<>(0.8, "id2", fakeEmbedding, segment2),
                        new EmbeddingMatch<>(0.7, "id3", fakeEmbedding, segment3)
                )));

        List<String> result = ragService.getRelevantContext("test", 2, "user-123");

        assertThat(result).hasSize(2);
        verify(queryRewritingService, never()).rewriteQuery(anyString());
    }

    @Test
    void searchSimilar_limitsRewriteVariantsToThree() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(queryRewritingService.rewriteQuery("test"))
                .thenReturn(List.of("test", "expanded-a", "expanded-b", "expanded-c"));
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(Embedding.from(new float[]{0.1f})));
        when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(List.of()));

        ragService.searchSimilar("test", 10);

        verify(embeddingStore, times(3)).search(any(EmbeddingSearchRequest.class));
    }

    @Test
    void searchSimilar_reranksOnlyTopCandidates() {
        ReflectionTestUtils.setField(ragService, "rerankMaxCandidates", 3);
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(queryRewritingService.rewriteQuery("test")).thenReturn(List.of("test"));
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(Embedding.from(new float[]{0.1f})));

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TextSegment segment = TextSegment.from("content " + i,
                    Metadata.from("noteId", "note-" + i).put("userId", "user-123"));
            matches.add(new EmbeddingMatch<>(0.9 - (i * 0.01), "id-" + i,
                    Embedding.from(new float[]{0.1f}), segment));
        }
        when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(matches));
        when(resilientLlmService.isRerankAvailable()).thenReturn(true);
        when(resilientLlmService.scoreAll(any(), anyString()))
                .thenReturn(Response.from(List.of(0.9, 0.8, 0.7, 0.6, 0.5)));
        ragService.searchSimilar("test", 10);

        org.mockito.ArgumentCaptor<List<TextSegment>> segments =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(resilientLlmService).scoreAll(segments.capture(), org.mockito.ArgumentMatchers.eq("test"));
        assertThat(segments.getValue()).hasSize(3);
    }

    @Test
    @DisplayName("generateEmbeddingAsync should handle missing note")
    void shouldHandleNonExistentNote() {
        when(noteRepository.findById("nonexistent")).thenReturn(Optional.empty());

        ragService.generateEmbeddingAsync("nonexistent");

        verifyNoInteractions(embeddingStore);
    }

    @Test
    void generateEmbeddingAsync_skipsUnchangedContentHash() {
        when(noteRepository.findById("note-456")).thenReturn(Optional.of(testNote));
        when(noteMediaRepository.findByNoteId("note-456")).thenReturn(List.of());
        when(structureAwareSplitter.split(any(Document.class))).thenReturn(List.of(
                TextSegment.from("chunk 1", Metadata.from("noteId", "note-456")),
                TextSegment.from("chunk 2", Metadata.from("noteId", "note-456"))
        ));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("note-456"), anyString()))
                .thenReturn(2);

        ragService.generateEmbeddingAsync("note-456");

        verify(resilientLlmService, never()).embedAll(any());
        verifyNoInteractions(embeddingStore);
    }

    @Test
    void generateEmbeddingAsync_usesBatchEmbeddingAndStoresContentHashMetadata() {
        when(noteRepository.findById("note-456")).thenReturn(Optional.of(testNote));
        when(noteMediaRepository.findByNoteId("note-456")).thenReturn(List.of());
        when(structureAwareSplitter.split(any(Document.class))).thenReturn(List.of(
                TextSegment.from("chunk 1", Metadata.from("noteId", "note-456")),
                TextSegment.from("chunk 2", Metadata.from("noteId", "note-456"))
        ));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("note-456"), anyString()))
                .thenReturn(0);
        when(resilientLlmService.embedAll(any())).thenReturn(Response.from(List.of(
                Embedding.from(new float[]{0.1f}),
                Embedding.from(new float[]{0.2f})
        )));

        ragService.generateEmbeddingAsync("note-456");

        ArgumentCaptor<List<TextSegment>> segmentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(resilientLlmService).embedAll(segmentsCaptor.capture());
        assertThat(segmentsCaptor.getValue()).hasSize(2);
        assertThat(segmentsCaptor.getValue().get(0).metadata().getString("contentHash")).isNotBlank();
        assertThat(segmentsCaptor.getValue().get(0).metadata().getString("chunkIndex")).isEqualTo("0");
        verify(embeddingStore, times(2)).add(any(Embedding.class), any(TextSegment.class));
    }
}
