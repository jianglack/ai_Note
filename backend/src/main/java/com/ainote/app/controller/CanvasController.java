package com.ainote.app.controller;

import com.ainote.app.entity.Canvas;
import com.ainote.app.model.CanvasRequest;
import com.ainote.app.repository.CanvasRepository;
import com.ainote.app.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/canvases")
public class CanvasController {

    private final CanvasRepository canvasRepository;
    private final SecurityUtils securityUtils;

    public CanvasController(CanvasRepository canvasRepository, SecurityUtils securityUtils) {
        this.canvasRepository = canvasRepository;
        this.securityUtils = securityUtils;
    }

    @PostMapping
    public ResponseEntity<Canvas> create(@Valid @RequestBody CanvasRequest req) {
        Canvas canvas = new Canvas();
        canvas.setUserId(securityUtils.getCurrentUserId());
        canvas.setTitle(req.getTitle() != null ? req.getTitle() : "Untitled Canvas");
        canvas.setData(req.getData() != null ? req.getData() : "{\"nodes\":[],\"edges\":[]}");
        return ResponseEntity.ok(canvasRepository.save(canvas));
    }

    @GetMapping
    public ResponseEntity<List<Canvas>> list() {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(canvasRepository.findByUserIdOrderByUpdatedAtDesc(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Canvas> get(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(canvasRepository.findByIdAndUserId(id, userId).orElseThrow());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Canvas> update(@PathVariable String id, @Valid @RequestBody CanvasRequest req) {
        String userId = securityUtils.getCurrentUserId();
        Canvas canvas = canvasRepository.findByIdAndUserId(id, userId).orElseThrow();
        if (req.getTitle() != null) canvas.setTitle(req.getTitle());
        if (req.getData() != null) canvas.setData(req.getData());
        return ResponseEntity.ok(canvasRepository.save(canvas));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        canvasRepository.findByIdAndUserId(id, userId).ifPresent(canvasRepository::delete);
        return ResponseEntity.ok().build();
    }
}
