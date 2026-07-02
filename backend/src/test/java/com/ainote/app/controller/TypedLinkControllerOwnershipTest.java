package com.ainote.app.controller;

import com.ainote.app.entity.Note;
import com.ainote.app.entity.TypedLink;
import com.ainote.app.model.TypedLinkRequest;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.TypedLinkRepository;
import com.ainote.app.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
class TypedLinkControllerOwnershipTest {
    private TypedLinkRepository typedLinkRepository;    private NoteRepository noteRepository;    private SecurityUtils securityUtils;
    private TypedLinkController controller;

    @BeforeEach
    void setUp() {
        typedLinkRepository = mock(TypedLinkRepository.class);
        noteRepository = mock(NoteRepository.class);
        securityUtils = mock(SecurityUtils.class);
        controller = new TypedLinkController(typedLinkRepository, noteRepository, securityUtils);
    }

    @Test
    void create_requiresBothNotesOwnedByCurrentUser() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("source", "user-1"))
                .thenReturn(Optional.of(new Note()));
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("target", "user-1"))
                .thenReturn(Optional.empty());

        TypedLinkRequest request = new TypedLinkRequest();
        request.setSourceNoteId("source");
        request.setTargetNoteId("target");
        request.setRelationType("related");

        assertThatThrownBy(() -> controller.create(request))
                .isInstanceOf(NoSuchElementException.class);

        verify(typedLinkRepository, never()).save(any());
    }

    @Test
    void getByNote_validatesNoteOwnershipAndUsesUserFilteredLookup() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("note-1", "user-1"))
                .thenReturn(Optional.of(new Note()));

        controller.getByNote("note-1");

        verify(noteRepository).findByIdAndUserIdAndDeletedAtIsNull("note-1", "user-1");
        verify(typedLinkRepository).findOwnedByNoteId("note-1", "user-1");
        verify(typedLinkRepository, never()).findBySourceNoteIdOrTargetNoteId("note-1", "note-1");
    }

    @Test
    void getByType_filtersLinksToCurrentUsersNotes() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");

        controller.getByType("related");

        verify(typedLinkRepository).findOwnedByRelationType("related", "user-1");
        verify(typedLinkRepository, never()).findByRelationType("related");
    }

    @Test
    void delete_deletesOnlyOwnedLink() {
        TypedLink link = new TypedLink();
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(typedLinkRepository.findOwnedById("link-1", "user-1")).thenReturn(Optional.of(link));

        controller.delete("link-1");

        verify(typedLinkRepository).delete(link);
        verify(typedLinkRepository, never()).deleteById("link-1");
    }

    @Test
    void delete_rejectsLinkOutsideCurrentUsersNotes() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(typedLinkRepository.findOwnedById("link-2", "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.delete("link-2"))
                .isInstanceOf(NoSuchElementException.class);

        verify(typedLinkRepository, never()).delete(any());
    }
}
