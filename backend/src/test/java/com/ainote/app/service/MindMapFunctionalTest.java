package com.ainote.app.service;

import com.ainote.app.entity.MindMap;
import com.ainote.app.entity.Note;
import com.ainote.app.repository.MindMapRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MindMapFunctionalTest {

    private MindMapRepository mindMapRepository;
    private NoteRepository noteRepository;
    private MindMapService service;

    @BeforeEach
    void setUp() {
        mindMapRepository = mock(MindMapRepository.class);
        noteRepository = mock(NoteRepository.class);
        SecurityUtils securityUtils = mock(SecurityUtils.class);
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        service = new MindMapService(mindMapRepository, noteRepository, securityUtils);
    }

    @Test
    void createListUpdateGetByNoteAndDeleteMindMap() {
        Note note = new Note();
        note.setId("note-1");
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("note-1", "user-1")).thenReturn(Optional.of(note));
        when(mindMapRepository.save(any(MindMap.class))).thenAnswer(invocation -> {
            MindMap mindMap = invocation.getArgument(0);
            if (mindMap.getId() == null) {
                mindMap.setId("mind-1");
            }
            return mindMap;
        });

        MindMap created = service.create("Map", "{\"nodes\":[]}", "note-1", null);

        assertThat(created.getUserId()).isEqualTo("user-1");
        assertThat(created.getSource()).isEqualTo("manual");

        when(mindMapRepository.findByUserIdOrderByUpdatedAtDesc("user-1")).thenReturn(List.of(created));
        assertThat(service.listByUser()).containsExactly(created);

        when(mindMapRepository.findByIdAndUserId("mind-1", "user-1")).thenReturn(Optional.of(created));
        MindMap updated = service.update("mind-1", "Updated", "{\"nodes\":[1]}");
        assertThat(updated.getTitle()).isEqualTo("Updated");
        assertThat(updated.getData()).contains("1");

        when(mindMapRepository.findByNoteIdAndUserIdOrderByUpdatedAtDesc("note-1", "user-1"))
                .thenReturn(List.of(created));
        assertThat(service.getByNoteId("note-1")).containsExactly(created);

        service.delete("mind-1");
        verify(mindMapRepository).delete(created);
    }

    @Test
    void createRejectsMindMapForNoteOutsideCurrentUser() {
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("note-1", "user-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("Map", "{}", "note-1", "manual"))
                .isInstanceOf(NoSuchElementException.class);
    }
}
