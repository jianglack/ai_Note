package com.ainote.app.service;

import com.ainote.app.entity.Annotation;
import com.ainote.app.entity.Note;
import com.ainote.app.entity.Tag;
import com.ainote.app.entity.User;
import com.ainote.app.repository.AnnotationRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.TagRepository;
import com.ainote.app.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnnotationServiceTest {

    private AnnotationRepository annotationRepository;
    private NoteRepository noteRepository;
    private TagRepository tagRepository;
    private SecurityUtils securityUtils;
    private AnnotationService service;

    private User user;
    private Note note;

    @BeforeEach
    void setUp() {
        annotationRepository = mock(AnnotationRepository.class);
        noteRepository = mock(NoteRepository.class);
        tagRepository = mock(TagRepository.class);
        securityUtils = mock(SecurityUtils.class);
        service = new AnnotationService(annotationRepository, noteRepository, tagRepository, securityUtils);

        user = new User();
        user.setId("user-1");
        user.setUsername("alice");

        note = new Note();
        note.setId("note-1");
        note.setTitle("Note");
        note.setContent("content");
        note.setUser(user);
        note.setCreatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        note.setUpdatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
    }

    @Test
    void createPersistsAnnotationAndAddsNewTagsToNote() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
        when(tagRepository.findByNameAndUserId("important", "user-1")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(annotationRepository.save(any(Annotation.class))).thenAnswer(invocation -> {
            Annotation annotation = invocation.getArgument(0);
            annotation.setId("ann-1");
            annotation.setCreatedAt(LocalDateTime.parse("2026-06-27T10:01:00"));
            annotation.setUpdatedAt(LocalDateTime.parse("2026-06-27T10:01:00"));
            return annotation;
        });

        com.ainote.app.model.Annotation result = service.create(
                "note-1", "selected", "comment", 1, 9, List.of("important"));

        assertThat(result.getId()).isEqualTo("ann-1");
        assertThat(result.getNoteId()).isEqualTo("note-1");
        assertThat(note.getTags()).extracting(Tag::getName).containsExactly("important");
        verify(noteRepository).save(note);
        verify(annotationRepository).save(any(Annotation.class));
    }

    @Test
    void createRejectsNotesOwnedByAnotherUser() {
        User other = new User();
        other.setId("other-user");
        note.setUser(other);

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> service.create("note-1", "selected", "comment", 0, 8, List.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unauthorized");

        verify(annotationRepository, never()).save(any());
    }

    @Test
    void getByNoteIdOrdersThroughRepositoryAndMapsModels() {
        Annotation annotation = annotation("ann-1", note, "selected", "comment", 2, 8);
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
        when(annotationRepository.findByNoteIdOrderByStartOffsetAsc("note-1")).thenReturn(List.of(annotation));

        List<com.ainote.app.model.Annotation> result = service.getByNoteId("note-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTextContent()).isEqualTo("selected");
        verify(annotationRepository).findByNoteIdOrderByStartOffsetAsc("note-1");
    }

    @Test
    void updateAndDeleteAreUserScoped() {
        Annotation annotation = annotation("ann-1", note, "selected", "old", 2, 8);
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(annotationRepository.findById("ann-1")).thenReturn(Optional.of(annotation));
        when(annotationRepository.save(annotation)).thenReturn(annotation);

        com.ainote.app.model.Annotation updated = service.update("ann-1", "new", List.of());
        service.delete("ann-1");

        assertThat(updated.getComment()).isEqualTo("new");
        verify(annotationRepository).save(annotation);
        verify(annotationRepository).delete(annotation);
    }

    @Test
    void deleteRejectsAnnotationsOwnedByAnotherUser() {
        Annotation annotation = annotation("ann-1", note, "selected", "comment", 2, 8);
        annotation.setUserId("other-user");

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(annotationRepository.findById("ann-1")).thenReturn(Optional.of(annotation));

        assertThatThrownBy(() -> service.delete("ann-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unauthorized");

        verify(annotationRepository, never()).delete(any());
    }

    private static Annotation annotation(String id, Note note, String text, String comment, int start, int end) {
        Annotation annotation = new Annotation();
        annotation.setId(id);
        annotation.setNote(note);
        annotation.setUserId(note.getUser().getId());
        annotation.setTextContent(text);
        annotation.setComment(comment);
        annotation.setStartOffset(start);
        annotation.setEndOffset(end);
        annotation.setCreatedAt(LocalDateTime.parse("2026-06-27T10:02:00"));
        annotation.setUpdatedAt(LocalDateTime.parse("2026-06-27T10:02:00"));
        return annotation;
    }
}
