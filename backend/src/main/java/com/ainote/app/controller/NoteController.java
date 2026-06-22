package com.ainote.app.controller;

import com.ainote.app.model.Note;
import com.ainote.app.model.NoteRequest;
import com.ainote.app.service.NoteService;
import com.ainote.app.service.NoteVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/notes")
@Tag(name = "笔记管理", description = "笔记的增删改查、搜索、回收站等功能")
@SecurityRequirement(name = "Bearer Authentication")
public class NoteController {
    private final NoteService noteService;
    private final NoteVersionService noteVersionService;

    public NoteController(NoteService noteService, NoteVersionService noteVersionService) {
        this.noteService = noteService;
        this.noteVersionService = noteVersionService;
    }

    @GetMapping
    public ResponseEntity<?> listAll(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null && size != null) {
            return ResponseEntity.ok(noteService.listAllPaged(PageRequest.of(page, size)));
        }
        return ResponseEntity.ok(noteService.listAll());
    }

    @PostMapping
    public Note create(@Valid @RequestBody NoteRequest request) {
        return noteService.create(request);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Note> get(@PathVariable String id) {
        return noteService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/versions")
    @Operation(summary = "获取笔记版本历史", description = "获取指定笔记的历史内容快照")
    public ResponseEntity<List<Map<String, Object>>> versions(@PathVariable String id) {
        if (noteService.getById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(noteVersionService.getVersions(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Note> update(@PathVariable String id, @Valid @RequestBody NoteRequest request) {
        return noteService.update(id, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        noteService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @Operation(summary = "搜索笔记", description = "使用混合搜索（关键词 + 语义）查找笔记")
    public List<Note> search(@RequestParam(name = "q", required = false) String q) {
        if (q == null || q.isBlank()) {
            return noteService.listAll();
        }
        return noteService.hybridSearch(q);
    }

    @GetMapping("/trash")
    @Operation(summary = "获取回收站笔记", description = "获取所有已删除的笔记")
    public List<Note> trash() {
        return noteService.listDeleted();
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "恢复笔记", description = "从回收站恢复笔记")
    public ResponseEntity<Void> restore(@PathVariable String id) {
        noteService.restore(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/permanent")
    @Operation(summary = "永久删除笔记", description = "从回收站永久删除笔记")
    public ResponseEntity<Void> permanentDelete(@PathVariable String id) {
        noteService.permanentDelete(id);
        return ResponseEntity.noContent().build();
    }
}

