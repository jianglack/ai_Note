package com.ainote.note.controller;

import com.ainote.note.model.NoteModel;
import com.ainote.note.model.NoteRequest;
import com.ainote.note.service.NoteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    public List<NoteModel> listAll() {
        return noteService.listAll();
    }

    @PostMapping
    public NoteModel create(@RequestBody NoteRequest request) {
        return noteService.create(request);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NoteModel> get(@PathVariable String id) {
        return noteService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<NoteModel> update(@PathVariable String id, @RequestBody NoteRequest request) {
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
    public List<NoteModel> search(@RequestParam(name = "q", required = false) String q) {
        if (q == null || q.isBlank()) {
            return noteService.listAll();
        }
        return noteService.search(q);
    }

    @GetMapping("/trash")
    public List<NoteModel> trash() {
        return noteService.listDeleted();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable String id) {
        noteService.restore(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> permanentDelete(@PathVariable String id) {
        noteService.permanentDelete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Endpoint for search-service reindex: paginated list of all notes.
     */
    @GetMapping("/all")
    public Page<NoteModel> listAllNotes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return noteService.findAll(PageRequest.of(page, size));
    }
}
