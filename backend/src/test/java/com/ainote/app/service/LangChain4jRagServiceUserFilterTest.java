package com.ainote.app.service;

import com.ainote.app.chunking.StructureAwareDocumentSplitter;
import com.ainote.app.repository.NoteMediaRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.security.SecurityUtils;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
@DisplayName("LangChain4jRagService userId Filter tests")
class LangChain4jRagServiceUserFilterTest {
    private EmbeddingModel embeddingModel;
    private EmbeddingStore<TextSegment> embeddingStore;
    private NoteRepository noteRepository;
    private SecurityUtils securityUtils;
    private QueryRewritingService queryRewritingService;
    private JdbcTemplate jdbcTemplate;
    private StructureAwareDocumentSplitter splitter;
    private ResilientLlmService resilientLlmService;
    private NoteMediaRepository noteMediaRepository;
    private LangChain4jRagService ragService;

    private static final String USER_ID = "user-abc";
    private static final String OTHER_USER_ID = "other-user";
    private static final Embedding FAKE_EMBEDDING = Embedding.from(new float[]{0.1f, 0.2f});

    @BeforeEach
    void setUp() {
        embeddingModel = mock(EmbeddingModel.class);
        embeddingStore = mock(EmbeddingStore.class);
        noteRepository = mock(NoteRepository.class);
        securityUtils = mock(SecurityUtils.class);
        queryRewritingService = mock(QueryRewritingService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        splitter = mock(StructureAwareDocumentSplitter.class);
        resilientLlmService = mock(ResilientLlmService.class);
        noteMediaRepository = mock(NoteMediaRepository.class);
        ragService = new LangChain4jRagService(
                embeddingModel, embeddingStore,
                noteRepository, securityUtils,
                null,
                queryRewritingService, jdbcTemplate,
                splitter, resilientLlmService, noteMediaRepository,
                10, 0.5);
    }

    @Test
    @DisplayName("searchWithMinScore: EmbeddingSearchRequest filter matches current user only")
    void searchWithMinScore_filterMatchesCurrentUser() {
        when(resilientLlmService.embed(any(TextSegment.class)))
                .thenReturn(Response.from(FAKE_EMBEDDING));
        when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(List.of()));

        ragService.searchWithMinScore("test query", 5, 0.5, USER_ID);

        ArgumentCaptor<EmbeddingSearchRequest> captor =
                ArgumentCaptor.forClass(EmbeddingSearchRequest.class);
        verify(embeddingStore).search(captor.capture());

        Filter filter = captor.getValue().filter();
        assertThat(filter).isNotNull();
        assertThat(filter.test(Metadata.from("userId", USER_ID))).isTrue();
        assertThat(filter.test(Metadata.from("userId", OTHER_USER_ID))).isFalse();
    }

    @Test
    @DisplayName("searchWithMinScore: Java userId and deletedAt filter still applies")
    void searchWithMinScore_javaLayerFilterStillApplied() {
        when(resilientLlmService.embed(any(TextSegment.class)))
                .thenReturn(Response.from(FAKE_EMBEDDING));

        TextSegment segment = TextSegment.from("text",
                Metadata.from("noteId", "note-1").put("userId", OTHER_USER_ID));
        EmbeddingMatch<TextSegment> match =
                new EmbeddingMatch<>(0.9, "id1", FAKE_EMBEDDING, segment);
        when(embeddingStore.search(any()))
                .thenReturn(new EmbeddingSearchResult<>(List.of(match)));

        when(noteRepository.findByIdsAndUserIdAndDeletedAtIsNull(List.of("note-1"), USER_ID))
                .thenReturn(List.of());

        var results = ragService.searchWithMinScore("test", 5, 0.5, USER_ID);
        assertThat(results).isEmpty();
        verify(noteRepository).findByIdsAndUserIdAndDeletedAtIsNull(List.of("note-1"), USER_ID);
    }

    @Test
    @DisplayName("searchSimilar: dynamic ContentRetriever EmbeddingSearchRequest includes userId filter")
    void searchSimilar_includesUserIdFilterInRetrieverSearch() {
        when(securityUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(queryRewritingService.rewriteQuery(any()))
                .thenReturn(List.of("rewritten"));
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(FAKE_EMBEDDING));
        when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(List.of()));

        ragService.searchSimilar("test", 5);

        ArgumentCaptor<EmbeddingSearchRequest> captor =
                ArgumentCaptor.forClass(EmbeddingSearchRequest.class);
        verify(embeddingStore).search(captor.capture());

        Filter filter = captor.getValue().filter();
        assertThat(filter).isNotNull();
        assertThat(filter.test(Metadata.from("userId", USER_ID))).isTrue();
        assertThat(filter.test(Metadata.from("userId", OTHER_USER_ID))).isFalse();
    }

    @Test
    @DisplayName("getRelevantContext: dynamic ContentRetriever EmbeddingSearchRequest includes userId filter")
    void getRelevantContext_includesUserIdFilterInRetrieverSearch() {
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(FAKE_EMBEDDING));
        when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(List.of()));

        ragService.getRelevantContext("test query", 5, USER_ID);

        ArgumentCaptor<EmbeddingSearchRequest> captor =
                ArgumentCaptor.forClass(EmbeddingSearchRequest.class);
        verify(embeddingStore).search(captor.capture());

        Filter filter = captor.getValue().filter();
        assertThat(filter).isNotNull();
        assertThat(filter.test(Metadata.from("userId", USER_ID))).isTrue();
        assertThat(filter.test(Metadata.from("userId", OTHER_USER_ID))).isFalse();
        verify(queryRewritingService, never()).rewriteQuery(anyString());
    }

    @Test
    @DisplayName("getRelevantContext: skips query rewriting")
    void getRelevantContext_skipsQueryRewriting() {
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(FAKE_EMBEDDING));
        when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(List.of()));

        ragService.getRelevantContext("test query", 5, USER_ID);
        verify(queryRewritingService, never()).rewriteQuery(anyString());
    }
}
