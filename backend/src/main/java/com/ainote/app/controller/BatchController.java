package com.ainote.app.controller;

import com.ainote.app.model.BatchArchiveRequest;
import com.ainote.app.model.BatchMoveRequest;
import com.ainote.app.model.BatchNoteRequest;
import com.ainote.app.model.BatchOperationResult;
import com.ainote.app.model.BatchTagRequest;
import com.ainote.app.service.BatchNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notes/batch")
@Tag(name = "批量操作", description = "笔记的批量删除、恢复、移动、标签、归档")
@SecurityRequirement(name = "Bearer Authentication")
public class BatchController {

    private static final int MAX_BATCH_SIZE = 100;

    private final BatchNoteService batchNoteService;

    public BatchController(BatchNoteService batchNoteService) {
        this.batchNoteService = batchNoteService;
    }

    @PostMapping("/delete")
    @Operation(summary = "批量软删除")
    public ResponseEntity<BatchOperationResult> batchDelete(@Valid @RequestBody BatchNoteRequest request) {
        ResponseEntity<BatchOperationResult> validation = validateRequest(request);
        if (validation != null) return validation;
        return ResponseEntity.ok(batchNoteService.batchDelete(request.getNoteIds()));
    }

    @PostMapping("/permanent-delete")
    @Operation(summary = "批量永久删除")
    public ResponseEntity<BatchOperationResult> batchPermanentDelete(@Valid @RequestBody BatchNoteRequest request) {
        ResponseEntity<BatchOperationResult> validation = validateRequest(request);
        if (validation != null) return validation;
        return ResponseEntity.ok(batchNoteService.batchPermanentDelete(request.getNoteIds()));
    }

    @PostMapping("/restore")
    @Operation(summary = "批量恢复")
    public ResponseEntity<BatchOperationResult> batchRestore(@Valid @RequestBody BatchNoteRequest request) {
        ResponseEntity<BatchOperationResult> validation = validateRequest(request);
        if (validation != null) return validation;
        return ResponseEntity.ok(batchNoteService.batchRestore(request.getNoteIds()));
    }

    @PostMapping("/move")
    @Operation(summary = "批量移动到文件夹")
    public ResponseEntity<BatchOperationResult> batchMove(@Valid @RequestBody BatchMoveRequest request) {
        ResponseEntity<BatchOperationResult> validation = validateRequest(request);
        if (validation != null) return validation;
        return ResponseEntity.ok(batchNoteService.batchMove(request.getNoteIds(), request.getFolderId()));
    }

    @PostMapping("/tag")
    @Operation(summary = "批量添加标签")
    public ResponseEntity<BatchOperationResult> batchAddTag(@Valid @RequestBody BatchTagRequest request) {
        ResponseEntity<BatchOperationResult> validation = validateRequest(request);
        if (validation != null) return validation;
        if (request.getTagName() == null || request.getTagName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(batchNoteService.batchAddTag(request.getNoteIds(), request.getTagName()));
    }

    @PostMapping("/remove-tag")
    @Operation(summary = "批量移除标签")
    public ResponseEntity<BatchOperationResult> batchRemoveTag(@Valid @RequestBody BatchTagRequest request) {
        ResponseEntity<BatchOperationResult> validation = validateRequest(request);
        if (validation != null) return validation;
        if (request.getTagName() == null || request.getTagName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(batchNoteService.batchRemoveTag(request.getNoteIds(), request.getTagName()));
    }

    @PostMapping("/archive")
    @Operation(summary = "批量归档/取消归档")
    public ResponseEntity<BatchOperationResult> batchArchive(@Valid @RequestBody BatchArchiveRequest request) {
        ResponseEntity<BatchOperationResult> validation = validateRequest(request);
        if (validation != null) return validation;
        return ResponseEntity.ok(batchNoteService.batchArchive(request.getNoteIds(), request.isArchive()));
    }

    private ResponseEntity<BatchOperationResult> validateRequest(BatchNoteRequest request) {
        if (request.getNoteIds() == null || request.getNoteIds().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.getNoteIds().size() > MAX_BATCH_SIZE) {
            return ResponseEntity.badRequest().build();
        }
        return null;
    }
}
