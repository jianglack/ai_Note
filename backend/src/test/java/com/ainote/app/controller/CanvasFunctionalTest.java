package com.ainote.app.controller;

import com.ainote.app.entity.Canvas;
import com.ainote.app.model.CanvasRequest;
import com.ainote.app.repository.CanvasRepository;
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

class CanvasFunctionalTest {

    private CanvasRepository canvasRepository;
    private SecurityUtils securityUtils;
    private CanvasController controller;

    @BeforeEach
    void setUp() {
        canvasRepository = mock(CanvasRepository.class);
        securityUtils = mock(SecurityUtils.class);
        controller = new CanvasController(canvasRepository, securityUtils);
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
    }

    @Test
    void createListUpdateAndDeleteCanvas() {
        when(canvasRepository.save(any(Canvas.class))).thenAnswer(invocation -> {
            Canvas canvas = invocation.getArgument(0);
            if (canvas.getId() == null) {
                canvas.setId("canvas-1");
            }
            return canvas;
        });
        CanvasRequest create = new CanvasRequest();

        Canvas created = controller.create(create).getBody();

        assertThat(created.getUserId()).isEqualTo("user-1");
        assertThat(created.getTitle()).isEqualTo("Untitled Canvas");
        assertThat(created.getData()).isEqualTo("{\"nodes\":[],\"edges\":[]}");

        when(canvasRepository.findByUserIdOrderByUpdatedAtDesc("user-1")).thenReturn(List.of(created));
        assertThat(controller.list().getBody()).containsExactly(created);

        when(canvasRepository.findByIdAndUserId("canvas-1", "user-1")).thenReturn(Optional.of(created));
        CanvasRequest update = new CanvasRequest();
        update.setTitle("Updated");
        update.setData("{\"nodes\":[{\"id\":\"n1\"}],\"edges\":[]}");

        Canvas updated = controller.update("canvas-1", update).getBody();

        assertThat(updated.getTitle()).isEqualTo("Updated");
        assertThat(updated.getData()).contains("n1");

        controller.delete("canvas-1");
        verify(canvasRepository).delete(created);
    }
}
