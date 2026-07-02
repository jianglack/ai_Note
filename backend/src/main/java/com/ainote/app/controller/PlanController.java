package com.ainote.app.controller;

import com.ainote.app.model.PlanModifyStepRequest;
import com.ainote.app.model.PlanRouteRequest;
import com.ainote.app.model.PlanSmartChatRequest;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.PlanningAgentService;
import com.ainote.app.service.planning.LlmTaskRouterService;
import com.ainote.app.service.planning.PlanProgressEmitter;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.Semaphore;

@RestController
@RequestMapping("/api/ai")
public class PlanController {

    private static final int MAX_CONCURRENT_SMART_CHAT = 8;

    private final PlanningAgentService planningService;
    private final PlanProgressEmitter progressEmitter;
    private final SecurityUtils securityUtils;
    private final LlmTaskRouterService taskRouter;
    private final Semaphore smartChatPermits = new Semaphore(MAX_CONCURRENT_SMART_CHAT);

    public PlanController(PlanningAgentService planningService,
                          PlanProgressEmitter progressEmitter,
                          SecurityUtils securityUtils,
                          LlmTaskRouterService taskRouter) {
        this.planningService = planningService;
        this.progressEmitter = progressEmitter;
        this.securityUtils = securityUtils;
        this.taskRouter = taskRouter;
    }

    @PostMapping("/smart-chat")
    public ResponseEntity<?> smartChat(@Valid @RequestBody PlanSmartChatRequest request) {
        if (!smartChatPermits.tryAcquire()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "AGENT_BUSY", "message", "AI agent is busy"));
        }
        String userId = securityUtils.getCurrentUserId();
        try {
            return ResponseEntity.ok(planningService.smartChat(
                    request.getQuery(), request.getNoteIds(), userId, Boolean.TRUE.equals(request.getForcePlan())));
        } finally {
            smartChatPermits.release();
        }
    }

    @PostMapping("/route")
    public ResponseEntity<?> route(@Valid @RequestBody PlanRouteRequest request) {
        return ResponseEntity.ok(taskRouter.route(request.getQuery(), request.getNoteIds()));
    }

    @GetMapping("/plans")
    public ResponseEntity<?> listPlans() {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(planningService.listPlans(userId));
    }

    @GetMapping("/plans/{id}")
    public ResponseEntity<?> getPlan(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(planningService.getPlanDetail(id, userId));
    }

    @PutMapping("/plans/{id}/approve")
    public ResponseEntity<?> approvePlan(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        planningService.approvePlan(id, userId);
        return ResponseEntity.ok(Map.of("status", "executing"));
    }

    @PutMapping("/plans/{id}/pause")
    public ResponseEntity<?> pausePlan(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        planningService.pausePlan(id, userId);
        return ResponseEntity.ok(Map.of("status", "paused"));
    }

    @PutMapping("/plans/{id}/resume")
    public ResponseEntity<?> resumePlan(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        planningService.resumePlan(id, userId);
        return ResponseEntity.ok(Map.of("status", "resuming"));
    }

    @PutMapping("/plans/{id}/cancel")
    public ResponseEntity<?> cancelPlan(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        planningService.cancelPlan(id, userId);
        return ResponseEntity.ok(Map.of("status", "cancelled"));
    }

    @GetMapping(value = "/plans/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPlanProgress(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        planningService.getPlanForUser(id, userId); // 验证权限
        return progressEmitter.subscribe(id);
    }

    @PutMapping("/plans/{id}/rollback")
    public ResponseEntity<?> rollbackPlan(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        planningService.rollbackPlan(id, userId);
        return ResponseEntity.ok(Map.of("status", "rolling_back"));
    }

    @PutMapping("/plans/{planId}/steps/{stepId}/skip")
    public ResponseEntity<?> skipStep(@PathVariable String planId, @PathVariable String stepId) {
        String userId = securityUtils.getCurrentUserId();
        planningService.skipStep(planId, stepId, userId);
        return ResponseEntity.ok(Map.of("status", "skipped"));
    }

    @PutMapping("/plans/{planId}/steps/{stepId}/modify")
    public ResponseEntity<?> modifyStep(@PathVariable String planId, @PathVariable String stepId,
                                         @Valid @RequestBody PlanModifyStepRequest body) {
        String userId = securityUtils.getCurrentUserId();
        planningService.modifyStep(planId, stepId, body.getParams(), userId);
        return ResponseEntity.ok(Map.of("status", "modified"));
    }
}
