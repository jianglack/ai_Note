package com.ainote.app.controller;

import com.ainote.app.model.memory.MemoryReplayCandidateExportResponse;
import com.ainote.app.model.memory.MemoryReviewCaseListResponse;
import com.ainote.app.model.memory.MemoryReviewCaseResponse;
import com.ainote.app.model.memory.MemoryReviewDecisionRequest;
import com.ainote.app.security.AdminAccessGuard;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.MemoryReviewService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/memory-review-cases")
@Tag(name = "Memory Review Admin", description = "Administrative memory feedback review workflow")
@SecurityRequirement(name = "Bearer Authentication")
public class MemoryReviewAdminController {

    private final MemoryReviewService memoryReviewService;
    private final SecurityUtils securityUtils;
    private final AdminAccessGuard adminAccessGuard;

    public MemoryReviewAdminController(MemoryReviewService memoryReviewService,
                                       SecurityUtils securityUtils,
                                       AdminAccessGuard adminAccessGuard) {
        this.memoryReviewService = memoryReviewService;
        this.securityUtils = securityUtils;
        this.adminAccessGuard = adminAccessGuard;
    }

    @GetMapping
    public MemoryReviewCaseListResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit) {
        adminAccessGuard.checkAdminAccess();
        return memoryReviewService.listReviewCases(status, limit);
    }

    @PostMapping("/{id}/decision")
    public MemoryReviewCaseResponse decide(@PathVariable Long id,
                                           @RequestBody MemoryReviewDecisionRequest request) {
        adminAccessGuard.checkAdminAccess();
        return memoryReviewService.decideReviewCase(
                id,
                securityUtils.getCurrentUserId(),
                request);
    }

    @GetMapping("/replay-candidates")
    public MemoryReplayCandidateExportResponse replayCandidates(
            @RequestParam(required = false) Integer limit) {
        adminAccessGuard.checkAdminAccess();
        return memoryReviewService.exportApprovedReplayCandidates(limit);
    }
}
