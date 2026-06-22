package com.ainote.app.controller;

import com.ainote.app.entity.AiWorkflow;
import com.ainote.app.entity.AiWorkflowRun;
import com.ainote.app.model.WorkflowRequest;
import com.ainote.app.repository.AiWorkflowRepository;
import com.ainote.app.repository.AiWorkflowRunRepository;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.WorkflowExecutionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final AiWorkflowRepository workflowRepository;
    private final AiWorkflowRunRepository runRepository;
    private final SecurityUtils securityUtils;
    private final WorkflowExecutionService workflowExecutionService;

    public WorkflowController(AiWorkflowRepository workflowRepository, AiWorkflowRunRepository runRepository,
                              SecurityUtils securityUtils, WorkflowExecutionService workflowExecutionService) {
        this.workflowRepository = workflowRepository;
        this.runRepository = runRepository;
        this.securityUtils = securityUtils;
        this.workflowExecutionService = workflowExecutionService;
    }

    @PostMapping
    public ResponseEntity<AiWorkflow> create(@Valid @RequestBody WorkflowRequest req) {
        String userId = securityUtils.getCurrentUserId();
        AiWorkflow wf = new AiWorkflow();
        wf.setUserId(userId);
        wf.setName(req.getName() != null ? req.getName() : "New Workflow");
        wf.setDescription(req.getDescription());
        wf.setTriggerType(req.getTriggerType() != null ? req.getTriggerType() : "manual");
        wf.setTriggerConfig(req.getTriggerConfig() != null ? req.getTriggerConfig() : "{}");
        wf.setSteps(req.getSteps() != null ? req.getSteps() : "[]");
        return ResponseEntity.ok(workflowRepository.save(wf));
    }

    @GetMapping
    public ResponseEntity<List<AiWorkflow>> list() {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(workflowRepository.findByUserIdOrderByUpdatedAtDesc(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AiWorkflow> get(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(workflowRepository.findByIdAndUserId(id, userId).orElseThrow());
    }

    @PutMapping("/{id}")
    public ResponseEntity<AiWorkflow> update(@PathVariable String id, @Valid @RequestBody WorkflowRequest req) {
        String userId = securityUtils.getCurrentUserId();
        AiWorkflow wf = workflowRepository.findByIdAndUserId(id, userId).orElseThrow();
        if (req.getName() != null) wf.setName(req.getName());
        if (req.getDescription() != null) wf.setDescription(req.getDescription());
        if (req.getTriggerType() != null) wf.setTriggerType(req.getTriggerType());
        if (req.getTriggerConfig() != null) wf.setTriggerConfig(req.getTriggerConfig());
        if (req.getSteps() != null) wf.setSteps(req.getSteps());
        return ResponseEntity.ok(workflowRepository.save(wf));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        workflowRepository.findByIdAndUserId(id, userId).ifPresent(workflowRepository::delete);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/toggle")
    public ResponseEntity<AiWorkflow> toggle(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        AiWorkflow wf = workflowRepository.findByIdAndUserId(id, userId).orElseThrow();
        wf.setEnabled(!wf.getEnabled());
        return ResponseEntity.ok(workflowRepository.save(wf));
    }

    @PostMapping("/{id}/run")
    public ResponseEntity<Map<String, String>> run(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        workflowRepository.findByIdAndUserId(id, userId).orElseThrow();
        AiWorkflowRun run = new AiWorkflowRun();
        run.setWorkflowId(id);
        run.setStatus("queued");
        runRepository.save(run);
        // 异步执行工作流
        workflowExecutionService.executeWorkflow(id, run.getId(), userId);
        return ResponseEntity.ok(Map.of("runId", run.getId(), "status", "queued"));
    }

    @GetMapping("/{id}/runs")
    public ResponseEntity<List<AiWorkflowRun>> getRuns(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        workflowRepository.findByIdAndUserId(id, userId).orElseThrow();
        return ResponseEntity.ok(runRepository.findByWorkflowIdOrderByStartedAtDesc(id));
    }
}
