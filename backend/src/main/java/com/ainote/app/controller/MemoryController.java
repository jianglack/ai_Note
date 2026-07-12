package com.ainote.app.controller;

import com.ainote.app.model.memory.MemoryForgetRequest;
import com.ainote.app.model.memory.MemoryForgetResponse;
import com.ainote.app.model.memory.MemoryDeleteProofResponse;
import com.ainote.app.model.memory.MemoryEventListResponse;
import com.ainote.app.model.memory.MemoryExportResponse;
import com.ainote.app.model.memory.MemoryFeedbackRequest;
import com.ainote.app.model.memory.MemoryListResponse;
import com.ainote.app.model.memory.MemoryResponse;
import com.ainote.app.model.memory.MemoryReviewCaseResponse;
import com.ainote.app.model.memory.MemoryUpdateRequest;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.MemoryControlService;
import com.ainote.app.service.MemoryReviewService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memories")
@Tag(name = "Memory Control", description = "User-visible long-term AI memory controls")
@SecurityRequirement(name = "Bearer Authentication")
public class MemoryController {

    private final MemoryControlService memoryControlService;
    private final MemoryReviewService memoryReviewService;
    private final SecurityUtils securityUtils;

    public MemoryController(MemoryControlService memoryControlService,
                            MemoryReviewService memoryReviewService,
                            SecurityUtils securityUtils) {
        this.memoryControlService = memoryControlService;
        this.memoryReviewService = memoryReviewService;
        this.securityUtils = securityUtils;
    }

    @GetMapping
    public MemoryListResponse list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String cursor) {
        return memoryControlService.listMemories(
                securityUtils.getCurrentUserId(), type, status, query, cursor);
    }

    @GetMapping("/events")
    public MemoryEventListResponse events(@RequestParam(required = false) Long memoryId,
                                          @RequestParam(required = false) Integer limit) {
        return memoryControlService.listEvents(securityUtils.getCurrentUserId(), memoryId, limit);
    }

    @PatchMapping("/{id}")
    public MemoryResponse update(@PathVariable Long id,
                                 @RequestBody MemoryUpdateRequest request) {
        return memoryControlService.updateMemory(securityUtils.getCurrentUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        memoryControlService.deleteMemory(securityUtils.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forget")
    public MemoryForgetResponse forget(@RequestBody MemoryForgetRequest request) {
        return memoryControlService.forgetMemories(securityUtils.getCurrentUserId(), request);
    }

    @PostMapping("/hard-delete")
    public MemoryDeleteProofResponse hardDelete(@RequestBody MemoryForgetRequest request) {
        return memoryControlService.hardDeleteMemories(securityUtils.getCurrentUserId(), request);
    }

    @PostMapping("/{id}/feedback")
    public MemoryReviewCaseResponse feedback(@PathVariable Long id,
                                             @RequestBody MemoryFeedbackRequest request) {
        return memoryReviewService.createFeedback(securityUtils.getCurrentUserId(), id, request);
    }

    @GetMapping("/export")
    public MemoryExportResponse export() {
        return memoryControlService.exportMemories(securityUtils.getCurrentUserId());
    }
}
