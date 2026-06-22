package com.ainote.app.service;

import com.ainote.app.entity.MindMap;
import com.ainote.app.repository.MindMapRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MindMapServiceOwnershipTest {

    @Mock
    private MindMapRepository mindMapRepository;

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private SecurityUtils securityUtils;

    private MindMapService mindMapService;

    @BeforeEach
    void setUp() {
        mindMapService = new MindMapService(mindMapRepository, noteRepository, securityUtils);
    }

    @Test
    void create_rejectsNoteNotOwnedByCurrentUser() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("note-1", "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mindMapService.create("title", "{}", "note-1", "note"))
                .isInstanceOf(NoSuchElementException.class);

        verify(mindMapRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getById_usesCurrentUserOwnershipLookup() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(mindMapRepository.findByIdAndUserId("mindmap-1", "user-1")).thenReturn(Optional.of(new MindMap()));

        mindMapService.getById("mindmap-1");

        verify(mindMapRepository).findByIdAndUserId("mindmap-1", "user-1");
        verify(mindMapRepository, never()).findById("mindmap-1");
    }

    @Test
    void getByNoteId_usesCurrentUserOwnershipLookup() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(mindMapRepository.findByNoteIdAndUserIdOrderByUpdatedAtDesc("note-1", "user-1"))
                .thenReturn(List.of());

        mindMapService.getByNoteId("note-1");

        verify(mindMapRepository).findByNoteIdAndUserIdOrderByUpdatedAtDesc("note-1", "user-1");
        verify(mindMapRepository, never()).findByNoteId("note-1");
    }
}
