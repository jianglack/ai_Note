package com.ainote.app.controller;

import com.ainote.app.entity.*;
import com.ainote.app.model.EvalDatasetRequest;
import com.ainote.app.model.EvalItemRequest;
import com.ainote.app.model.EvalRunRequest;
import com.ainote.app.service.EvaluationService;
import com.ainote.app.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final EvaluationService evaluationService;
    private final SecurityUtils securityUtils;

    public EvalController(EvaluationService evaluationService, SecurityUtils securityUtils) {
        this.evaluationService = evaluationService;
        this.securityUtils = securityUtils;
    }

    // ── Datasets ──

    @PostMapping("/datasets")
    public ResponseEntity<EvalDataset> createDataset(@Valid @RequestBody EvalDatasetRequest req) {
        EvalDataset ds = evaluationService.createDataset(
            req.getName(), req.getDescription(), req.getDatasetType());
        return ResponseEntity.ok(ds);
    }

    @GetMapping("/datasets")
    public ResponseEntity<List<EvalDataset>> listDatasets() {
        return ResponseEntity.ok(evaluationService.listDatasets());
    }

    @GetMapping("/datasets/{id}")
    public ResponseEntity<Map<String, Object>> getDataset(@PathVariable String id) {
        EvalDataset ds = evaluationService.getDataset(id);
        List<EvalItem> items = evaluationService.getItems(id);
        return ResponseEntity.ok(Map.of("dataset", ds, "items", items));
    }

    @DeleteMapping("/datasets/{id}")
    public ResponseEntity<Void> deleteDataset(@PathVariable String id) {
        evaluationService.deleteDataset(id);
        return ResponseEntity.ok().build();
    }

    // ── Items ──

    @PostMapping("/datasets/{datasetId}/items")
    public ResponseEntity<EvalItem> addItem(
            @PathVariable String datasetId, @Valid @RequestBody EvalItemRequest req) {
        EvalItem item = evaluationService.addItem(
            datasetId, req.getQuestion(), req.getExpectedAnswer(), req.getExpectedToolCalls());
        return ResponseEntity.ok(item);
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> deleteItem(@PathVariable String itemId) {
        evaluationService.deleteItem(itemId);
        return ResponseEntity.ok().build();
    }

    // ── Runs ──

    @PostMapping("/run")
    public ResponseEntity<Map<String, String>> startRun(@Valid @RequestBody EvalRunRequest req) {
        String datasetId = req.getDatasetId();
        String runType = req.getRunType() != null ? req.getRunType() : "full";
        String config = req.getConfig() != null ? req.getConfig() : "{}";
        String userId = securityUtils.getCurrentUserId();
        evaluationService.runEvaluation(datasetId, runType, config, userId);
        return ResponseEntity.ok(Map.of("status", "started", "message", "Evaluation started"));
    }

    @GetMapping("/runs")
    public ResponseEntity<List<EvalRun>> listRuns() {
        return ResponseEntity.ok(evaluationService.listRuns());
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<Map<String, Object>> getRun(@PathVariable String id) {
        EvalRun run = evaluationService.getRun(id);
        List<EvalResult> results = evaluationService.getResults(id);
        return ResponseEntity.ok(Map.of("run", run, "results", results));
    }
}
