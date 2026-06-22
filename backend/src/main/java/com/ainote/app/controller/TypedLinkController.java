package com.ainote.app.controller;

import com.ainote.app.entity.TypedLink;
import com.ainote.app.model.TypedLinkRequest;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.TypedLinkRepository;
import com.ainote.app.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/typed-links")
public class TypedLinkController {

    private final TypedLinkRepository typedLinkRepository;
    private final NoteRepository noteRepository;
    private final SecurityUtils securityUtils;

    public TypedLinkController(
            TypedLinkRepository typedLinkRepository,
            NoteRepository noteRepository,
            SecurityUtils securityUtils) {
        this.typedLinkRepository = typedLinkRepository;
        this.noteRepository = noteRepository;
        this.securityUtils = securityUtils;
    }

    @PostMapping
    public ResponseEntity<TypedLink> create(@Valid @RequestBody TypedLinkRequest req) {
        String userId = securityUtils.getCurrentUserId();
        String sourceNoteId = requireNoteId(req.getSourceNoteId());
        String targetNoteId = requireNoteId(req.getTargetNoteId());
        requireOwnedNote(sourceNoteId, userId);
        requireOwnedNote(targetNoteId, userId);

        TypedLink link = new TypedLink();
        link.setSourceNoteId(sourceNoteId);
        link.setTargetNoteId(targetNoteId);
        String relationType = req.getRelationType() != null ? req.getRelationType() : req.getLinkType();
        link.setRelationType(relationType != null ? relationType : "related");
        link.setContext(req.getContext());
        return ResponseEntity.ok(typedLinkRepository.save(link));
    }

    @GetMapping("/note/{noteId}")
    public ResponseEntity<List<TypedLink>> getByNote(@PathVariable String noteId) {
        String userId = securityUtils.getCurrentUserId();
        requireOwnedNote(noteId, userId);
        return ResponseEntity.ok(typedLinkRepository.findOwnedByNoteId(noteId, userId));
    }

    @GetMapping("/type/{relationType}")
    public ResponseEntity<List<TypedLink>> getByType(@PathVariable String relationType) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(typedLinkRepository.findOwnedByRelationType(relationType, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        TypedLink link = typedLinkRepository.findOwnedById(id, userId).orElseThrow();
        typedLinkRepository.delete(link);
        return ResponseEntity.ok().build();
    }

    private void requireOwnedNote(String noteId, String userId) {
        noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId).orElseThrow();
    }

    private String requireNoteId(String noteId) {
        if (noteId == null || noteId.isBlank()) {
            throw new NoSuchElementException("Note not found");
        }
        return noteId;
    }
}
