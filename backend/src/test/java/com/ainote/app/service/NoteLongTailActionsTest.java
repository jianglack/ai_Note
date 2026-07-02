package com.ainote.app.service;

import com.ainote.app.entity.Folder;
import com.ainote.app.entity.Note;
import com.ainote.app.entity.Tag;
import com.ainote.app.entity.User;
import com.ainote.app.repository.FolderRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.NoteVersionRepository;
import com.ainote.app.repository.TagRepository;
import com.ainote.app.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoteLongTailActionsTest {

    private NoteRepository noteRepository;
    private SecurityUtils securityUtils;
    private LangChain4jRagService ragService;
    private KnowledgeGraphService knowledgeGraphService;
    private NoteService service;

    private User user;

    @BeforeEach
    void setUp() {
        noteRepository = mock(NoteRepository.class);
        securityUtils = mock(SecurityUtils.class);
        ragService = mock(LangChain4jRagService.class);
        knowledgeGraphService = mock(KnowledgeGraphService.class);
        service = new NoteService(
                noteRepository,
                mock(NoteVersionRepository.class),
                mock(TagRepository.class),
                mock(FolderRepository.class),
                securityUtils,
                mock(CacheService.class),
                ragService,
                knowledgeGraphService,
                mock(ContentAnalysisService.class));

        user = new User();
        user.setId("user-1");
        user.setUsername("alice");
    }

    @Test
    void copyDuplicatesContentFolderAndTagsForCurrentUserOnly() {
        Note original = note("note-1", "Original", "body");
        Folder folder = new Folder();
        folder.setId("folder-1");
        original.setFolder(folder);
        original.getTags().add(new Tag("tag-1", "work", "user-1"));

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("note-1", "user-1"))
                .thenReturn(Optional.of(original));
        when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionSynchronizationManager.initSynchronization();
        try {
            com.ainote.app.model.Note copy = service.copy("note-1");

            assertThat(copy.getTitle()).contains("副本");
            assertThat(copy.getContent()).isEqualTo("body");
            assertThat(copy.getFolderId()).isEqualTo("folder-1");
            assertThat(copy.getTags()).extracting(com.ainote.app.model.Tag::getName).containsExactly("work");

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(ragService).generateEmbeddingAsync(copy.getId());
            verify(knowledgeGraphService).syncNote(copy.getId());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void copyReturnsNullAndDoesNotSaveWhenNoteIsNotOwnedByCurrentUser() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("other-note", "user-1"))
                .thenReturn(Optional.empty());

        com.ainote.app.model.Note result = service.copy("other-note");

        assertThat(result).isNull();
        verify(noteRepository, never()).save(any());
    }

    @Test
    void mergeCombinesOwnedNotesContentTagsAndFirstFolder() {
        Folder folder = new Folder();
        folder.setId("folder-1");
        Note first = note("note-1", "First", "first body");
        first.setFolder(folder);
        first.getTags().add(new Tag("tag-1", "work", "user-1"));
        Note second = note("note-2", "Second", "second body");
        second.getTags().add(new Tag("tag-2", "idea", "user-1"));

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("note-1", "user-1"))
                .thenReturn(Optional.of(first));
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("note-2", "user-1"))
                .thenReturn(Optional.of(second));
        when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionSynchronizationManager.initSynchronization();
        try {
            com.ainote.app.model.Note merged = service.merge(List.of("note-1", "note-2"), "Merged");

            assertThat(merged.getTitle()).isEqualTo("Merged");
            assertThat(merged.getContent()).contains("## First", "first body", "## Second", "second body");
            assertThat(merged.getFolderId()).isEqualTo("folder-1");
            assertThat(merged.getTags()).extracting(com.ainote.app.model.Tag::getName)
                    .containsExactlyInAnyOrder("work", "idea");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void emptyTrashDeletesOnlyCurrentUsersDeletedNotesAndSyncsAfterCommit() {
        Note deleted = note("note-1", "Deleted", "body");
        deleted.setDeletedAt(LocalDateTime.parse("2026-06-27T10:05:00"));
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(noteRepository.findByUserIdAndDeletedAtIsNotNull("user-1")).thenReturn(List.of(deleted));

        int count = service.emptyTrash();

        assertThat(count).isEqualTo(1);
        verify(noteRepository).delete(deleted);
        verify(knowledgeGraphService).deleteNote("note-1");
    }

    private Note note(String id, String title, String content) {
        Note note = new Note();
        note.setId(id);
        note.setTitle(title);
        note.setContent(content);
        note.setUser(user);
        note.setCreatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        note.setUpdatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        return note;
    }
}
