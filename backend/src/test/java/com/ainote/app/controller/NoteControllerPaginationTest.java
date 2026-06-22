package com.ainote.app.controller;

import com.ainote.app.model.Note;
import com.ainote.app.service.NoteService;
import com.ainote.app.service.NoteVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteControllerPaginationTest {

    @Mock
    private NoteService noteService;

    @Mock
    private NoteVersionService noteVersionService;

    private NoteController controller;
    private Note note;

    @BeforeEach
    void setUp() {
        controller = new NoteController(noteService, noteVersionService);
        note = new Note();
        note.setId("note-1");
        note.setTitle("Test Note");
    }

    @Test
    void listAllWithoutPagingParamsReturnsLegacyList() {
        when(noteService.listAll()).thenReturn(List.of(note));

        ResponseEntity<?> response = controller.listAll(null, null);

        assertThat(response.getBody()).isEqualTo(List.of(note));
        verify(noteService).listAll();
        verify(noteService, never()).listAllPaged(any(Pageable.class));
    }

    @Test
    void listAllWithPageAndSizeReturnsPage() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(noteService.listAllPaged(pageable))
                .thenReturn(new PageImpl<>(List.of(note), pageable, 1));

        ResponseEntity<?> response = controller.listAll(0, 20);

        assertThat(response.getBody()).isInstanceOf(PageImpl.class);
        verify(noteService).listAllPaged(pageable);
        verify(noteService, never()).listAll();
    }

    @Test
    void listAllWithOnlyPageParamFallsBackToLegacyList() {
        when(noteService.listAll()).thenReturn(List.of(note));

        ResponseEntity<?> response = controller.listAll(0, null);

        assertThat(response.getBody()).isEqualTo(List.of(note));
        verify(noteService).listAll();
        verify(noteService, never()).listAllPaged(any(Pageable.class));
    }
}
