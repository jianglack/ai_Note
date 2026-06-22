package com.ainote.app.controller;

import com.ainote.app.model.AdminAgentEvaluationRequest;
import com.ainote.app.model.AdminRagEvaluationRequest;
import com.ainote.app.repository.UserMemoryRepository;
import com.ainote.app.security.AdminAccessGuard;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.AgentEvaluationService;
import com.ainote.app.service.AgentMetricsService;
import com.ainote.app.service.RagEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Administrative endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminController {

    private final UserMemoryRepository userMemoryRepository;
    private final RagEvaluationService ragEvaluationService;
    private final AgentEvaluationService agentEvaluationService;
    private final AgentMetricsService agentMetricsService;
    private final SecurityUtils securityUtils;
    private final AdminAccessGuard adminAccessGuard;

    public AdminController(
            UserMemoryRepository userMemoryRepository,
            RagEvaluationService ragEvaluationService,
            AgentEvaluationService agentEvaluationService,
            AgentMetricsService agentMetricsService,
            SecurityUtils securityUtils,
            AdminAccessGuard adminAccessGuard) {
        this.userMemoryRepository = userMemoryRepository;
        this.ragEvaluationService = ragEvaluationService;
        this.agentEvaluationService = agentEvaluationService;
        this.agentMetricsService = agentMetricsService;
        this.securityUtils = securityUtils;
        this.adminAccessGuard = adminAccessGuard;
    }

    @DeleteMapping("/clear-all-memory")
    @Operation(summary = "Clear all chat memory")
    public ResponseEntity<String> clearAllMemory() {
        adminAccessGuard.checkAdminAccess();
        long count = userMemoryRepository.count();
        userMemoryRepository.deleteAll();
        return ResponseEntity.ok("Cleared " + count + " memory records");
    }

    @GetMapping("/agent-metrics")
    @Operation(summary = "Get agent metrics")
    public ResponseEntity<Map<String, Object>> getAgentMetrics() {
        adminAccessGuard.checkAdminAccess();
        return ResponseEntity.ok(agentMetricsService.getMetrics());
    }

    @PostMapping("/eval/rag")
    @Operation(summary = "Evaluate RAG retrieval")
    public ResponseEntity<RagEvaluationService.EvalReport> evaluateRag(
            @Valid @RequestBody AdminRagEvaluationRequest body) {
        adminAccessGuard.checkAdminAccess();
        String userId = securityUtils.getCurrentUserId();
        int k = body.getK() != null ? body.getK() : 5;
        double minScore = body.getMinScore() != null ? body.getMinScore() : 0.5;

        List<RagEvaluationService.EvalCase> cases = body.getCases().stream().map(c -> {
            RagEvaluationService.EvalCase ec = new RagEvaluationService.EvalCase();
            ec.query = c.getQuery();
            List<String> ids = c.getExpectedNoteIds();
            ec.expectedNoteIds = ids != null ? ids : List.of();
            return ec;
        }).toList();

        RagEvaluationService.EvalReport report = ragEvaluationService.evaluate(cases, k, minScore, userId);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/eval/agent")
    @Operation(summary = "Evaluate agent behavior")
    public ResponseEntity<AgentEvaluationService.AgentEvalReport> evaluateAgent(
            @Valid @RequestBody AdminAgentEvaluationRequest body) {
        adminAccessGuard.checkAdminAccess();
        String userId = securityUtils.getCurrentUserId();

        List<AgentEvaluationService.AgentEvalCase> cases = body.getCases().stream().map(c -> {
            AgentEvaluationService.AgentEvalCase ec = new AgentEvaluationService.AgentEvalCase();
            ec.userQuery = c.getUserQuery();
            ec.expectedToolName = c.getExpectedToolName();
            ec.expectedAction = c.getExpectedAction();
            ec.expectSuccess = c.getExpectSuccess() != null ? c.getExpectSuccess() : true;
            List<String> keywords = c.getExpectedResponseKeywords();
            ec.expectedResponseKeywords = keywords != null ? keywords : List.of();
            return ec;
        }).toList();

        AgentEvaluationService.AgentEvalReport report = agentEvaluationService.evaluate(cases, userId);
        return ResponseEntity.ok(report);
    }
}
