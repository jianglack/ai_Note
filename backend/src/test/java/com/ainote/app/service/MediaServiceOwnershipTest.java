package com.ainote.app.service;

import com.ainote.app.entity.NoteMedia;
import com.ainote.app.repository.NoteMediaRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceOwnershipTest {

    @Mock
    private NoteMediaRepository mediaRepository;

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private LangChain4jRagService ragService;

    @Mock
    private OcrService ocrService;

    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new MediaService(mediaRepository, noteRepository, securityUtils, ragService, ocrService);
    }

    @Test
    void uploadImage_rejectsNoteNotOwnedByCurrentUser() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1, 2, 3});
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("note-1", "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mediaService.uploadImage("note-1", file, "alt"))
                .isInstanceOf(NoSuchElementException.class);

        verify(mediaRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getById_usesCurrentUserOwnershipLookup() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(mediaRepository.findByIdAndUserId("media-1", "user-1")).thenReturn(Optional.of(new NoteMedia()));

        mediaService.getById("media-1");

        verify(mediaRepository).findByIdAndUserId("media-1", "user-1");
        verify(mediaRepository, never()).findById("media-1");
    }

    @Test
    void getByNoteId_usesCurrentUserOwnershipLookup() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");

        mediaService.getByNoteId("note-1");

        verify(mediaRepository).findByNoteIdAndUserId("note-1", "user-1");
        verify(mediaRepository, never()).findByNoteId("note-1");
    }
}
