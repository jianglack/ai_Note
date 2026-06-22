package com.ainote.app.controller;

import com.ainote.app.entity.Canvas;
import com.ainote.app.model.CanvasRequest;
import com.ainote.app.repository.CanvasRepository;
import com.ainote.app.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CanvasControllerOwnershipTest {

    @Mock
    private CanvasRepository canvasRepository;

    @Mock
    private SecurityUtils securityUtils;

    private CanvasController controller;

    @BeforeEach
    void setUp() {
        controller = new CanvasController(canvasRepository, securityUtils);
    }

    @Test
    void get_usesCurrentUserOwnershipLookup() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(canvasRepository.findByIdAndUserId("canvas-1", "user-1")).thenReturn(Optional.of(new Canvas()));

        controller.get("canvas-1");

        verify(canvasRepository).findByIdAndUserId("canvas-1", "user-1");
        verify(canvasRepository, never()).findById("canvas-1");
    }

    @Test
    void update_rejectsCanvasNotOwnedByCurrentUser() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(canvasRepository.findByIdAndUserId("canvas-1", "user-1")).thenReturn(Optional.empty());

        CanvasRequest request = new CanvasRequest();
        request.setTitle("new");

        assertThatThrownBy(() -> controller.update("canvas-1", request))
                .isInstanceOf(NoSuchElementException.class);

        verify(canvasRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(canvasRepository, never()).findById("canvas-1");
    }

    @Test
    void delete_onlyDeletesCanvasOwnedByCurrentUser() {
        Canvas canvas = new Canvas();
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(canvasRepository.findByIdAndUserId("canvas-1", "user-1")).thenReturn(Optional.of(canvas));

        controller.delete("canvas-1");

        verify(canvasRepository).delete(canvas);
        verify(canvasRepository, never()).deleteById("canvas-1");
    }
}
