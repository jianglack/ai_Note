package com.ainote.app.controller;

import com.ainote.app.entity.NoteDatabase;
import com.ainote.app.entity.NoteDatabaseRow;
import com.ainote.app.model.NoteDatabaseRequest;
import com.ainote.app.model.NoteDatabaseRowRequest;
import com.ainote.app.repository.NoteDatabaseRepository;
import com.ainote.app.repository.NoteDatabaseRowRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/note-databases")
public class NoteDatabaseController {

    private final NoteDatabaseRepository dbRepository;
    private final NoteDatabaseRowRepository rowRepository;
    private final NoteRepository noteRepository;
    private final SecurityUtils securityUtils;

    public NoteDatabaseController(
            NoteDatabaseRepository dbRepository,
            NoteDatabaseRowRepository rowRepository,
            NoteRepository noteRepository,
            SecurityUtils securityUtils) {
        this.dbRepository = dbRepository;
        this.rowRepository = rowRepository;
        this.noteRepository = noteRepository;
        this.securityUtils = securityUtils;
    }

    @PostMapping
    public ResponseEntity<NoteDatabase> create(
            @Validated(NoteDatabaseRequest.Create.class) @RequestBody NoteDatabaseRequest req) {
        String userId = securityUtils.getCurrentUserId();
        String noteId = requireNoteId(req.getNoteId());
        noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId).orElseThrow();
        NoteDatabase db = new NoteDatabase();
        db.setNoteId(noteId);
        db.setUserId(userId);
        db.setName(req.getName() != null ? req.getName() : "Untitled Database");
        db.setColumns(req.getColumns() != null ? req.getColumns() : "[]");
        db.setViewConfig(req.getViewConfig() != null ? req.getViewConfig() : "{}");
        return ResponseEntity.ok(dbRepository.save(db));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
        NoteDatabase db = getDatabaseForCurrentUser(id);
        List<NoteDatabaseRow> rows = rowRepository.findByDatabaseIdOrderBySortOrderAsc(id);
        return ResponseEntity.ok(Map.of("database", db, "rows", rows));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NoteDatabase> update(@PathVariable String id, @Valid @RequestBody NoteDatabaseRequest req) {
        NoteDatabase db = getDatabaseForCurrentUser(id);
        if (req.getName() != null) db.setName(req.getName());
        if (req.getColumns() != null) db.setColumns(req.getColumns());
        if (req.getViewConfig() != null) db.setViewConfig(req.getViewConfig());
        return ResponseEntity.ok(dbRepository.save(db));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        NoteDatabase db = getDatabaseForCurrentUser(id);
        dbRepository.delete(db);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/note/{noteId}")
    public ResponseEntity<List<NoteDatabase>> getByNote(@PathVariable String noteId) {
        String userId = securityUtils.getCurrentUserId();
        noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId).orElseThrow();
        return ResponseEntity.ok(dbRepository.findByNoteIdAndUserId(noteId, userId));
    }

    // ── Rows ──

    @PostMapping("/{id}/rows")
    public ResponseEntity<NoteDatabaseRow> addRow(@PathVariable String id, @Valid @RequestBody NoteDatabaseRowRequest req) {
        getDatabaseForCurrentUser(id);
        NoteDatabaseRow row = new NoteDatabaseRow();
        row.setDatabaseId(id);
        row.setData(req.getData() != null ? req.getData() : "{}");
        row.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        return ResponseEntity.ok(rowRepository.save(row));
    }

    @PutMapping("/{id}/rows/{rowId}")
    public ResponseEntity<NoteDatabaseRow> updateRow(
            @PathVariable String id,
            @PathVariable String rowId,
            @Valid @RequestBody NoteDatabaseRowRequest req) {
        getDatabaseForCurrentUser(id);
        NoteDatabaseRow row = rowRepository.findByIdAndDatabaseId(rowId, id).orElseThrow();
        if (req.getData() != null) row.setData(req.getData());
        if (req.getSortOrder() != null) row.setSortOrder(req.getSortOrder());
        return ResponseEntity.ok(rowRepository.save(row));
    }

    @DeleteMapping("/{id}/rows/{rowId}")
    public ResponseEntity<Void> deleteRow(@PathVariable String id, @PathVariable String rowId) {
        getDatabaseForCurrentUser(id);
        NoteDatabaseRow row = rowRepository.findByIdAndDatabaseId(rowId, id).orElseThrow();
        rowRepository.delete(row);
        return ResponseEntity.ok().build();
    }

    private NoteDatabase getDatabaseForCurrentUser(String id) {
        String userId = securityUtils.getCurrentUserId();
        return dbRepository.findByIdAndUserId(id, userId).orElseThrow();
    }

    private String requireNoteId(String noteId) {
        if (noteId == null || noteId.isBlank()) {
            throw new NoSuchElementException("Note not found");
        }
        return noteId;
    }
}
