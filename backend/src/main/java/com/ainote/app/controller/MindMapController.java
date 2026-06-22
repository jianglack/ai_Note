package com.ainote.app.controller;

import com.ainote.app.entity.MindMap;
import com.ainote.app.model.MindMapRequest;
import com.ainote.app.service.MindMapService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mindmaps")
public class MindMapController {

    private final MindMapService mindMapService;

    public MindMapController(MindMapService mindMapService) {
        this.mindMapService = mindMapService;
    }

    @PostMapping
    public ResponseEntity<MindMap> create(@Valid @RequestBody MindMapRequest request) {
        MindMap mindMap = mindMapService.create(
            request.getTitle(),
            request.getData(),
            request.getNoteId(),
            request.getSource()
        );
        return ResponseEntity.ok(mindMap);
    }

    @GetMapping
    public ResponseEntity<List<MindMap>> list() {
        return ResponseEntity.ok(mindMapService.listByUser());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MindMap> getById(@PathVariable String id) {
        return ResponseEntity.ok(mindMapService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MindMap> update(@PathVariable String id, @Valid @RequestBody MindMapRequest request) {
        MindMap updated = mindMapService.update(id, request.getTitle(), request.getData());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        mindMapService.delete(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/note/{noteId}")
    public ResponseEntity<List<MindMap>> getByNote(@PathVariable String noteId) {
        return ResponseEntity.ok(mindMapService.getByNoteId(noteId));
    }
}
