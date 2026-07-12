package com.ainote.app.service;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ainote.app.config.MemoryProperties;
import com.ainote.app.entity.EpisodicMemory;
import com.ainote.app.entity.Note;
import com.ainote.app.entity.SemanticMemory;
import com.ainote.app.entity.User;
import com.ainote.app.repository.EpisodicMemoryRepository;
import com.ainote.app.repository.FolderRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.SemanticMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
@DisplayName("ContextAssembler")
class ContextAssemblerTest {
    private NoteRepository noteRepository;
    private FolderRepository folderRepository;
    private LangChain4jRagService ragService;
    private SemanticMemoryRepository semanticMemoryRepository;
    private EpisodicMemoryRepository episodicMemoryRepository;
    private JiTokenService jiTokenService;
    private RagFeedbackService ragFeedbackService;
    private MemoryRetrievalService memoryRetrievalService;
    private MemoryProperties memoryProperties;
    private MemoryMetricsService memoryMetricsService;
    private ContextAssembler contextAssembler;

    @BeforeEach
    void setUp() {
        noteRepository = mock(NoteRepository.class);
        folderRepository = mock(FolderRepository.class);
        ragService = mock(LangChain4jRagService.class);
        semanticMemoryRepository = mock(SemanticMemoryRepository.class);
        episodicMemoryRepository = mock(EpisodicMemoryRepository.class);
        jiTokenService = mock(JiTokenService.class);
        ragFeedbackService = mock(RagFeedbackService.class);
        memoryRetrievalService = mock(MemoryRetrievalService.class);
        memoryProperties = new MemoryProperties();
        memoryMetricsService = mock(MemoryMetricsService.class);
        contextAssembler = new ContextAssembler(
                noteRepository,
                folderRepository,
                ragService,
                semanticMemoryRepository,
                episodicMemoryRepository,
                jiTokenService,
                ragFeedbackService,
                memoryRetrievalService,
                memoryProperties,
                memoryMetricsService
        );

        ReflectionTestUtils.setField(contextAssembler, "noteMaxChars", 800);
        ReflectionTestUtils.setField(contextAssembler, "snippetMaxChars", 300);
        ReflectionTestUtils.setField(contextAssembler, "totalBudgetTokens", 4000);
        ReflectionTestUtils.setField(contextAssembler, "budgetSemanticMemory", 500);
        ReflectionTestUtils.setField(contextAssembler, "budgetEpisodicMemory", 400);
        ReflectionTestUtils.setField(contextAssembler, "budgetUserOverview", 300);
        ReflectionTestUtils.setField(contextAssembler, "budgetSelectedNotes", 1500);
        ReflectionTestUtils.setField(contextAssembler, "budgetRagContext", 800);

        when(jiTokenService.countTokens(anyString()))
                .thenAnswer(inv -> Math.max(1, ((String) inv.getArgument(0)).length() / 4));
    }

    @Test
    @DisplayName("选中笔记应作为默认操作对象，RAG 结果只能作为参考材料")
    void shouldMarkSelectedNoteAsDefaultOperationTargetAndRagAsReferenceOnly() {
        User user = new User();
        user.setId("user-1");

        Note selected = new Note();
        selected.setId("selected-note");
        selected.setTitle("测试-上下文工程与记忆系统记录");
        selected.setContent("<p>当前选中的笔记内容</p>");
        selected.setUser(user);
        selected.setCreatedAt(LocalDateTime.now());
        selected.setUpdatedAt(LocalDateTime.now());

        com.ainote.app.model.Note ragHit = new com.ainote.app.model.Note();
        ragHit.setId("rag-note");
        ragHit.setTitle("测试-RAG 检索增强生成记录");
        ragHit.setContent("这是一条检索命中的参考笔记");

        when(semanticMemoryRepository.findTopByUserId(org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(episodicMemoryRepository.findRecentByUserId(org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(noteRepository.countByUserIdAndDeletedAtIsNull("user-1")).thenReturn(1L);
        when(folderRepository.findByUserId("user-1")).thenReturn(List.of());
        when(noteRepository.findByIdAndUserIdWithTagsAndFolder("selected-note", "user-1"))
                .thenReturn(Optional.of(selected));
        when(ragFeedbackService.getAdaptiveThreshold()).thenReturn(0.7);
        when(ragService.searchWithMinScore("删除笔记", 5, 0.7, "user-1"))
                .thenReturn(List.of(ragHit));

        String context = contextAssembler.assemble("删除笔记", List.of("selected-note"), "user-1");

        assertThat(context).contains("<selected_notes default_operation_target=\"true\">");
        assertThat(context).contains("<context_policy>");
        assertThat(context).contains("当前用户请求 > 明确选中的笔记");
        assertThat(context).contains("selected-note");
        assertThat(context).contains("测试-上下文工程与记忆系统记录");
        assertThat(context).contains("<rag_context role=\"reference_only\" operation_target=\"false\">");
        assertThat(context).contains("noteId=\"rag-note\" title=\"测试-RAG 检索增强生成记录\" operation_target=\"false\"");
        verify(ragService).searchWithMinScore(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyString());
        verify(noteRepository, never()).findByUserIdAndDeletedAtIsNull("user-1");
    }

    @Test
    @DisplayName("current-note questions should use the selected note only and avoid global RAG drift")
    void currentNoteQuestionShouldUseSelectedNoteOnlyAndAvoidGlobalRagDrift() {
        User user = new User();
        user.setId("user-1");

        Note selected = new Note();
        selected.setId("selected-note");
        selected.setTitle("Prompt Template Library");
        selected.setContent("<p>Prompt-specific content that should be summarized.</p>");
        selected.setUser(user);
        selected.setCreatedAt(LocalDateTime.now());
        selected.setUpdatedAt(LocalDateTime.now());

        com.ainote.app.model.Note ragHit = new com.ainote.app.model.Note();
        ragHit.setId("rag-note");
        ragHit.setTitle("RAG Study Note");
        ragHit.setContent("RAG content that must not drift into a current-note answer.");

        when(semanticMemoryRepository.findTopByUserId(org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(episodicMemoryRepository.findRecentByUserId(org.mockito.ArgumentMatchers.eq("user-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(noteRepository.countByUserIdAndDeletedAtIsNull("user-1")).thenReturn(2L);
        when(folderRepository.findByUserId("user-1")).thenReturn(List.of());
        when(noteRepository.findByIdAndUserIdWithTagsAndFolder("selected-note", "user-1"))
                .thenReturn(Optional.of(selected));
        when(ragFeedbackService.getAdaptiveThreshold()).thenReturn(0.7);
        when(ragService.searchWithMinScore("What are the key points of this note?", 5, 0.7, "user-1"))
                .thenReturn(List.of(ragHit));

        String context = contextAssembler.assemble(
                "What are the key points of this note?",
                List.of("selected-note"),
                "user-1"
        );

        assertThat(context).contains("<selected_notes default_operation_target=\"true\">");
        assertThat(context).contains("Prompt Template Library");
        assertThat(context).contains("Prompt-specific content");
        assertThat(context).doesNotContain("<rag_context");
        assertThat(context).doesNotContain("RAG Study Note");
        verify(ragService, never()).searchWithMinScore(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void currentNoteQuestionShouldNotInjectUnrelatedLongTermMemory() {
        User user = new User();
        user.setId("user-1");

        Note selected = new Note();
        selected.setId("selected-note");
        selected.setTitle("Release Notes");
        selected.setContent("<p>Selected note content only.</p>");
        selected.setUser(user);
        selected.setCreatedAt(LocalDateTime.now());
        selected.setUpdatedAt(LocalDateTime.now());

        SemanticMemory unrelatedPreference = new SemanticMemory();
        unrelatedPreference.setCategory("preference");
        unrelatedPreference.setContent("always mention unrelated database preferences");

        EpisodicMemory unrelatedEpisode = new EpisodicMemory();
        unrelatedEpisode.setSessionSummary("Old discussion about unrelated RAG tuning.");
        unrelatedEpisode.setCreatedAt(LocalDateTime.now().minusDays(3));

        when(semanticMemoryRepository.findTopByUserId(org.mockito.ArgumentMatchers.eq("user-1"), any()))
                .thenReturn(List.of(unrelatedPreference));
        when(episodicMemoryRepository.findRecentByUserId(org.mockito.ArgumentMatchers.eq("user-1"), any()))
                .thenReturn(List.of(unrelatedEpisode));
        when(noteRepository.countByUserIdAndDeletedAtIsNull("user-1")).thenReturn(1L);
        when(folderRepository.findByUserId("user-1")).thenReturn(List.of());
        when(noteRepository.findByIdAndUserIdWithTagsAndFolder("selected-note", "user-1"))
                .thenReturn(Optional.of(selected));

        String context = contextAssembler.assemble(
                "Summarize this note",
                List.of("selected-note"),
                "user-1"
        );

        assertThat(context).contains("Release Notes");
        assertThat(context).contains("Selected note content only");
        assertThat(context).doesNotContain("always mention unrelated database preferences");
        assertThat(context).doesNotContain("Old discussion about unrelated RAG tuning");
        verify(semanticMemoryRepository, never()).findTopByUserId(org.mockito.ArgumentMatchers.eq("user-1"), any());
        verify(episodicMemoryRepository, never()).findRecentByUserId(org.mockito.ArgumentMatchers.eq("user-1"), any());
    }

    @Test
    void legacyRetrievalModeUsesExistingRepositoriesAndLogsCounts() {
        SemanticMemory preference = new SemanticMemory();
        preference.setCategory("preference");
        preference.setContent("likes detailed plans");
        SemanticMemory style = new SemanticMemory();
        style.setCategory("style");
        style.setContent("prefers Chinese responses");

        EpisodicMemory episode = new EpisodicMemory();
        episode.setSessionSummary("Discussed memory system optimization.");
        episode.setCreatedAt(LocalDateTime.now());

        when(semanticMemoryRepository.findTopByUserId(org.mockito.ArgumentMatchers.eq("user-1"), any()))
                .thenReturn(List.of(preference, style));
        when(episodicMemoryRepository.findRecentByUserId(org.mockito.ArgumentMatchers.eq("user-1"), any()))
                .thenReturn(List.of(episode));
        when(noteRepository.countByUserIdAndDeletedAtIsNull("user-1")).thenReturn(0L);
        when(folderRepository.findByUserId("user-1")).thenReturn(List.of());
        when(ragFeedbackService.getAdaptiveThreshold()).thenReturn(0.7);
        when(ragService.searchWithMinScore(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of());

        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ContextAssembler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            String context = contextAssembler.assemble("write an implementation plan", List.of(), "user-1");

            assertThat(context).contains("<user_memory>");
            assertThat(context).startsWith("<context_policy>");
            assertThat(context).contains("长期记忆只用于用户偏好、交互风格和连续项目背景");
            assertThat(context).contains("likes detailed plans");
            assertThat(context).contains("<recent_sessions>");
            assertThat(context).contains("Discussed memory system optimization.");
            verify(semanticMemoryRepository)
                    .findTopByUserId(org.mockito.ArgumentMatchers.eq("user-1"), any());
            verify(episodicMemoryRepository)
                    .findRecentByUserId(org.mockito.ArgumentMatchers.eq("user-1"), any());
            assertThat(appender.list)
                    .anySatisfy(event -> assertThat(event.getFormattedMessage())
                            .contains("memory_context_event=semantic_retrieved")
                            .contains("user_id=user-1")
                            .contains("retrieval_mode=legacy")
                            .contains("memory_count=2"));
            assertThat(appender.list)
                    .anySatisfy(event -> assertThat(event.getFormattedMessage())
                            .contains("memory_context_event=episodic_retrieved")
                            .contains("user_id=user-1")
                            .contains("memory_count=1"));
            assertThat(appender.list)
                    .anySatisfy(event -> assertThat(event.getFormattedMessage())
                            .contains("memory_context_event=assembled")
                            .contains("intent=STANDARD")
                            .contains("retrieval_mode=legacy")
                            .contains("estimated_tokens="));
            verify(memoryMetricsService).recordContextInjection(eq("semantic"), eq(2), anyInt());
            verify(memoryMetricsService).recordContextInjection(eq("episodic"), eq(1), anyInt());
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void queryRelevantRetrievalModeUsesMemoryRetrievalServiceInsteadOfLegacyTopN() {
        memoryProperties.getRetrieval().setMode(MemoryProperties.RetrievalMode.QUERY_RELEVANT);

        SemanticMemory preference = new SemanticMemory();
        preference.setCategory("preference");
        preference.setContent("prefers selected note summaries without unrelated memory drift");

        EpisodicMemory episode = new EpisodicMemory();
        episode.setSessionSummary("Discussed selected-note summary isolation.");
        episode.setCreatedAt(LocalDateTime.now());

        when(memoryRetrievalService.retrieveForQuery(
                org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.eq("summarize this note"),
                org.mockito.ArgumentMatchers.eq(10),
                org.mockito.ArgumentMatchers.eq(3)
        )).thenReturn(new MemoryRetrievalService.MemoryRetrievalResult(
                List.of(preference),
                List.of(episode)
        ));
        when(noteRepository.countByUserIdAndDeletedAtIsNull("user-1")).thenReturn(0L);
        when(folderRepository.findByUserId("user-1")).thenReturn(List.of());
        when(ragFeedbackService.getAdaptiveThreshold()).thenReturn(0.7);
        when(ragService.searchWithMinScore(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(List.of());

        String context = contextAssembler.assemble("summarize this note", List.of(), "user-1");

        assertThat(context).contains("<user_memory>");
        assertThat(context).contains("prefers selected note summaries without unrelated memory drift");
        assertThat(context).contains("<recent_sessions>");
        assertThat(context).contains("Discussed selected-note summary isolation.");
        verify(memoryRetrievalService).retrieveForQuery("user-1", "summarize this note", 10, 3);
        verify(semanticMemoryRepository, never()).findTopByUserId(org.mockito.ArgumentMatchers.eq("user-1"), any());
        verify(episodicMemoryRepository, never()).findRecentByUserId(org.mockito.ArgumentMatchers.eq("user-1"), any());
        verify(memoryMetricsService).recordContextInjection(eq("semantic"), eq(1), anyInt());
        verify(memoryMetricsService).recordContextInjection(eq("episodic"), eq(1), anyInt());
    }
}
