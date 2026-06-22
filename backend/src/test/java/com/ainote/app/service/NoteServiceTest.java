package com.ainote.app.service;

import com.ainote.app.entity.Folder;
import com.ainote.app.entity.Note;
import com.ainote.app.entity.User;
import com.ainote.app.repository.FolderRepository;
import com.ainote.app.repository.NoteVersionRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.TagRepository;
import com.ainote.app.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NoteService 单元测试")
class NoteServiceTest {

    @Mock
    private NoteRepository noteRepository;
    @Mock
    private NoteVersionRepository noteVersionRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private FolderRepository folderRepository;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private CacheService cacheService;
    @Mock
    private LangChain4jRagService langChain4jRagService;
    @Mock
    private KnowledgeGraphService knowledgeGraphService;
    @Mock
    private ContentAnalysisService contentAnalysisService;

    private NoteService noteService;

    private User testUser;
    private Note testNote;

    @BeforeEach
    void setUp() {
        noteService = new NoteService(
            noteRepository, noteVersionRepository, tagRepository, folderRepository,
            securityUtils, cacheService, langChain4jRagService, knowledgeGraphService,
            contentAnalysisService
        );

        testUser = new User();
        testUser.setId("user-123");
        testUser.setUsername("testuser");

        testNote = new Note();
        testNote.setId("note-456");
        testNote.setTitle("测试笔记");
        testNote.setContent("<p>测试内容</p>");
        testNote.setUser(testUser);
        testNote.setCreatedAt(LocalDateTime.now());
        testNote.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("listAll 应返回当前用户的所有笔记")
    void shouldListAllNotesForCurrentUser() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(noteRepository.findByUserIdWithTagsAndFolder("user-123"))
            .thenReturn(List.of(testNote));

        List<com.ainote.app.model.Note> result = noteService.listAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("note-456");
        assertThat(result.get(0).getTitle()).isEqualTo("测试笔记");
        verify(noteRepository, never()).findByUserIdAndDeletedAtIsNull("user-123");
    }

    @Test
    @DisplayName("countAll should use repository count query")
    void shouldCountAllNotesForCurrentUser() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(noteRepository.countByUserIdAndDeletedAtIsNull("user-123")).thenReturn(42L);

        long result = noteService.countAll();

        assertThat(result).isEqualTo(42L);
        verify(noteRepository).countByUserIdAndDeletedAtIsNull("user-123");
        verify(noteRepository, never()).findByUserIdAndDeletedAtIsNull("user-123");
    }

    @Test
    @DisplayName("listAllPaged should preserve total count for empty out-of-range pages")
    void shouldListAllPagedPreserveTotalCountWhenPageIsEmpty() {
        PageRequest pageable = PageRequest.of(99, 20);
        Page<String> idPage = new PageImpl<>(List.of(), pageable, 25);

        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(noteRepository.findNoteIdsByUserId("user-123", pageable)).thenReturn(idPage);

        Page<com.ainote.app.model.Note> result = noteService.listAllPaged(pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(25);
        assertThat(result.getTotalPages()).isEqualTo(2);
        verify(noteRepository, never()).findByIdsWithTagsAndFolder(anyList(), anyString());
    }

    @Test
    @DisplayName("getById 应返回指定笔记")
    void shouldGetNoteById() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("note-456", "user-123"))
            .thenReturn(Optional.of(testNote));

        Optional<com.ainote.app.model.Note> result = noteService.getById("note-456");

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("测试笔记");
    }

    @Test
    @DisplayName("getById 笔记不存在时应返回空")
    void shouldReturnEmptyWhenNoteNotFound() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull(anyString(), anyString()))
            .thenReturn(Optional.empty());

        Optional<com.ainote.app.model.Note> result = noteService.getById("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("delete 应设置 deletedAt (软删除)")
    void shouldSoftDeleteNote() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("note-456", "user-123"))
            .thenReturn(Optional.of(testNote));

        noteService.delete("note-456");

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("restore 应清除 deletedAt")
    void shouldRestoreNote() {
        testNote.setDeletedAt(LocalDateTime.now());
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(noteRepository.findByIdAndUserId("note-456", "user-123")).thenReturn(Optional.of(testNote));

        noteService.restore("note-456");

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNull();
        verify(noteRepository, never()).findById("note-456");
    }

    @Test
    @DisplayName("listDeleted 应返回已删除的笔记")
    void shouldListDeletedNotes() {
        testNote.setDeletedAt(LocalDateTime.now());
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(noteRepository.findByUserIdAndDeletedAtIsNotNull("user-123"))
            .thenReturn(List.of(testNote));

        List<com.ainote.app.model.Note> result = noteService.listDeleted();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("note-456");
    }

    @Test
    @DisplayName("search 应返回匹配的笔记")
    void shouldSearchNotes() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(noteRepository.searchByUserIdAndQuery("user-123", "测试"))
            .thenReturn(List.of(testNote));

        List<com.ainote.app.model.Note> result = noteService.search("测试");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("测试笔记");
    }

    @Test
    @DisplayName("hybridSearch 空查询应返回所有笔记")
    void shouldReturnAllNotesForEmptySearch() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(noteRepository.findByUserIdWithTagsAndFolder("user-123"))
            .thenReturn(List.of(testNote));

        List<com.ainote.app.model.Note> result = noteService.hybridSearch("");

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("update 应在修改前保存旧内容为版本快照")
    void shouldCreateVersionSnapshotBeforeUpdate() {
        com.ainote.app.model.NoteRequest request = new com.ainote.app.model.NoteRequest();
        request.setTitle("更新后的标题");
        request.setContent("<p>更新后的内容</p>");

        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("note-456", "user-123"))
            .thenReturn(Optional.of(testNote));
        when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));

        noteService.update("note-456", request);

        verify(noteVersionRepository).save(argThat(version ->
            version.getNote() == testNote
                && "<p>测试内容</p>".equals(version.getContent())
                && version.getCreatedAt() != null
        ));
    }

    @Test
    @DisplayName("hybridSearch 非空查询应调用语义搜索")
    void shouldCallSemanticSearchForNonEmptyQuery() {
        when(langChain4jRagService.searchSimilar("Java", 10))
            .thenReturn(List.of());

        noteService.hybridSearch("Java");

        verify(langChain4jRagService).searchSimilar("Java", 10);
    }

    @Test
    @DisplayName("update should generate embedding only after transaction commit")
    void update_generatesEmbeddingAfterCommit() {
        com.ainote.app.model.NoteRequest request = new com.ainote.app.model.NoteRequest();
        request.setTitle("updated");
        request.setContent("updated content");

        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("note-456", "user-123"))
            .thenReturn(Optional.of(testNote));
        when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionSynchronizationManager.initSynchronization();
        try {
            noteService.update("note-456", request);

            verify(langChain4jRagService, never()).generateEmbeddingAsync("note-456");

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            verify(langChain4jRagService).generateEmbeddingAsync("note-456");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("permanentDelete 应彻底删除笔记")
    void shouldPermanentlyDeleteNote() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(noteRepository.findByIdAndUserId("note-456", "user-123")).thenReturn(Optional.of(testNote));

        noteService.permanentDelete("note-456");

        verify(noteRepository).delete(testNote);
        verify(noteRepository, never()).deleteById("note-456");
    }

    @Test
    void getByFolderId_usesUserBoundFolderQuery() {
        Folder folder = new Folder();
        folder.setId("folder-1");
        testNote.setFolder(folder);
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(noteRepository.findByFolderIdAndUserIdAndDeletedAtIsNull("folder-1", "user-123"))
            .thenReturn(List.of(testNote));

        List<com.ainote.app.model.Note> result = noteService.getByFolderId("folder-1");

        assertThat(result).hasSize(1);
        verify(noteRepository).findByFolderIdAndUserIdAndDeletedAtIsNull("folder-1", "user-123");
        verify(noteRepository, never()).findByFolderIdAndDeletedAtIsNull("folder-1");
    }

    @Test
    void moveToFolder_rejectsFolderNotOwnedByCurrentUser() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("note-456", "user-123"))
            .thenReturn(Optional.of(testNote));
        when(folderRepository.findByIdAndUserId("other-folder", "user-123"))
            .thenReturn(Optional.empty());

        Optional<com.ainote.app.model.Note> result = noteService.moveToFolder("note-456", "other-folder");

        assertThat(result).isEmpty();
        verify(noteRepository, never()).save(any());
        verify(folderRepository, never()).findById("other-folder");
    }
}
