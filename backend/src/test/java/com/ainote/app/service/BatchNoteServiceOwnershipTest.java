package com.ainote.app.service;

import com.ainote.app.entity.Folder;
import com.ainote.app.model.BatchOperationResult;
import com.ainote.app.repository.FolderRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.TagRepository;
import com.ainote.app.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
class BatchNoteServiceOwnershipTest {
    private NoteRepository noteRepository;    private TagRepository tagRepository;    private FolderRepository folderRepository;    private SecurityUtils securityUtils;    private KnowledgeGraphService knowledgeGraphService;
    private BatchNoteService batchNoteService;

    @BeforeEach
    void setUp() {
        noteRepository = mock(NoteRepository.class);
        tagRepository = mock(TagRepository.class);
        folderRepository = mock(FolderRepository.class);
        securityUtils = mock(SecurityUtils.class);
        knowledgeGraphService = mock(KnowledgeGraphService.class);
        batchNoteService = new BatchNoteService(
                noteRepository,
                tagRepository,
                folderRepository,
                securityUtils,
                knowledgeGraphService);
    }

    @Test
    void batchMove_rejectsTargetFolderNotOwnedByCurrentUser() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(folderRepository.findByIdAndUserId("other-folder", "user-123")).thenReturn(Optional.empty());

        BatchOperationResult result = batchNoteService.batchMove(List.of("note-1", "note-2"), "other-folder");

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isEqualTo(2);
        verify(folderRepository, never()).findById("other-folder");
        verify(noteRepository, never()).findByIdAndUserIdAndDeletedAtIsNull("note-1", "user-123");
    }

    @Test
    void batchMove_usesUserBoundFolderLookupForOwnedTargetFolder() {
        Folder folder = new Folder();
        folder.setId("folder-1");
        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(folderRepository.findByIdAndUserId("folder-1", "user-123")).thenReturn(Optional.of(folder));

        batchNoteService.batchMove(List.of(), "folder-1");

        verify(folderRepository).findByIdAndUserId("folder-1", "user-123");
        verify(folderRepository, never()).findById("folder-1");
    }
}
