package com.ainote.app.controller;

import com.ainote.app.entity.Note;
import com.ainote.app.entity.TypedLink;
import com.ainote.app.model.TypedLinkRequest;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.TypedLinkRepository;
import com.ainote.app.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TypedLinkFunctionalTest {

    private TypedLinkRepository typedLinkRepository;
    private NoteRepository noteRepository;
    private TypedLinkController controller;

    @BeforeEach
    void setUp() {
        typedLinkRepository = mock(TypedLinkRepository.class);
        noteRepository = mock(NoteRepository.class);
        SecurityUtils securityUtils = mock(SecurityUtils.class);
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        controller = new TypedLinkController(typedLinkRepository, noteRepository, securityUtils);
    }

    @Test
    void createQueryAndDeleteTypedLinksForOwnedNotes() {
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull(any(String.class), any(String.class)))
                .thenReturn(Optional.of(new Note()));
        when(typedLinkRepository.save(any(TypedLink.class))).thenAnswer(invocation -> {
            TypedLink link = invocation.getArgument(0);
            link.setId("link-1");
            return link;
        });

        TypedLinkRequest request = new TypedLinkRequest();
        request.setSourceNoteId("source");
        request.setTargetNoteId("target");
        request.setLinkType("supports");
        request.setContext("context");
        TypedLink link = controller.create(request).getBody();

        assertThat(link.getRelationType()).isEqualTo("supports");
        assertThat(link.getContext()).isEqualTo("context");

        when(typedLinkRepository.findOwnedByNoteId("source", "user-1")).thenReturn(List.of(link));
        assertThat(controller.getByNote("source").getBody()).containsExactly(link);

        when(typedLinkRepository.findOwnedByRelationType("supports", "user-1")).thenReturn(List.of(link));
        assertThat(controller.getByType("supports").getBody()).containsExactly(link);

        when(typedLinkRepository.findOwnedById("link-1", "user-1")).thenReturn(Optional.of(link));
        controller.delete("link-1");
        verify(typedLinkRepository).delete(link);
    }
}
