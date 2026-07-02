package com.ainote.app.service;

import com.ainote.app.entity.Note;
import com.ainote.app.entity.User;
import com.ainote.app.repository.EpisodicMemoryRepository;
import com.ainote.app.repository.FolderRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.SemanticMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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
        contextAssembler = new ContextAssembler(
                noteRepository,
                folderRepository,
                ragService,
                semanticMemoryRepository,
                episodicMemoryRepository,
                jiTokenService,
                ragFeedbackService
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
}
