package com.ainote.app.service.planning;

import com.ainote.app.entity.TaskPlan;
import com.ainote.app.entity.SideEffectJournal;
import com.ainote.app.entity.TaskStep;
import com.ainote.app.repository.TaskPlanRepository;
import com.ainote.app.repository.SideEffectJournalRepository;
import com.ainote.app.repository.TaskStepRepository;
import com.ainote.app.service.AgentMetricsService;
import com.ainote.app.service.AgentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CompensationService {

    private static final Logger log = LoggerFactory.getLogger(CompensationService.class);

    private final AgentService agentService;
    private final TaskPlanRepository planRepository;
    private final TaskStepRepository stepRepository;
    private final SideEffectJournalRepository sideEffectJournalRepository;
    private final ObjectMapper objectMapper;
    private final AgentMetricsService metricsService;

    public CompensationService(AgentService agentService,
                               TaskPlanRepository planRepository,
                               TaskStepRepository stepRepository,
                               SideEffectJournalRepository sideEffectJournalRepository,
                               ObjectMapper objectMapper,
                               AgentMetricsService metricsService) {
        this.agentService = agentService;
        this.planRepository = planRepository;
        this.stepRepository = stepRepository;
        this.sideEffectJournalRepository = sideEffectJournalRepository;
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
    }

    public void rollbackPlan(String planId, String userId) {
        TaskPlan plan = planRepository.findById(planId).orElseThrow();
        List<TaskStep> completedSteps = stepRepository.findByPlanIdAndStatusOrderByStepOrder(
                planId, TaskStep.STATUS_SUCCESS);

        if (completedSteps.isEmpty()) {
            log.info("Plan {} has no completed steps to rollback", planId);
            return;
        }

        log.info("Rolling back {} completed steps for plan {}", completedSteps.size(), planId);

        int rolledBack = 0;
        int verifiedFailed = 0;
        for (int i = completedSteps.size() - 1; i >= 0; i--) {
            TaskStep step = completedSteps.get(i);
            CompensationResult result = compensateAndVerify(step, plan, userId);
            metricsService.recordCompensation(result.executed, result.verified);

            if (result.verified) {
                step.setStatus(TaskStep.STATUS_ROLLED_BACK);
                rolledBack++;
            } else if (result.executed) {
                step.setStatus(TaskStep.STATUS_ROLLED_BACK);
                step.setErrorMessage("Compensation executed but verification uncertain: " + result.detail);
                rolledBack++;
                verifiedFailed++;
                log.warn("Step {} compensation executed but verification uncertain: {}",
                        step.getStepOrder(), result.detail);
            } else {
                step.setErrorMessage("Compensation failed: " + result.detail);
                log.warn("Compensation failed for step {} in plan {}: {}",
                        step.getStepOrder(), planId, result.detail);
            }
            stepRepository.save(step);
        }

        List<TaskStep> pendingSteps = stepRepository.findByPlanIdAndStatusOrderByStepOrder(
                planId, TaskStep.STATUS_PENDING);
        for (TaskStep step : pendingSteps) {
            step.setStatus(TaskStep.STATUS_SKIPPED);
            stepRepository.save(step);
        }

        if (rolledBack == completedSteps.size() && verifiedFailed == 0) {
            plan.setStatus(TaskPlan.STATUS_CANCELLED);
        } else {
            plan.setStatus(TaskPlan.STATUS_CANCELLED_PARTIAL);
        }
        planRepository.save(plan);

        log.info("Rollback complete for plan {}: {}/{} rolled back, {} verification uncertain",
                planId, rolledBack, completedSteps.size(), verifiedFailed);
    }

    private record CompensationResult(boolean executed, boolean verified, String detail) {}

    private CompensationResult compensateAndVerify(TaskStep step, TaskPlan plan, String userId) {
        Optional<SideEffectJournal> journalRecord = sideEffectJournalRepository
                .findByPlanIdAndStepIdOrderByCreatedAtDesc(plan.getId(), step.getId())
                .stream()
                .findFirst();
        String compensation = journalRecord
                .map(SideEffectJournal::getJournalJson)
                .orElse(step.getCompensation());
        if (compensation == null || compensation.isBlank()) {
            return new CompensationResult(false, false, "missing compensation journal");
        }

        String agentResponse = executeCompensationJson(step, compensation, plan, userId);
        if (agentResponse == null) {
            journalRecord.ifPresent(journal -> markJournal(journal, SideEffectJournal.STATUS_FAILED, null));
            return new CompensationResult(false, false, "compensation returned null");
        }

        boolean verified = verifyCompensation(agentResponse, step.getAction());
        journalRecord.ifPresent(journal -> markJournal(
                journal,
                verified ? SideEffectJournal.STATUS_VERIFIED : SideEffectJournal.STATUS_EXECUTED,
                agentResponse));
        return new CompensationResult(true, verified, verified ? "OK" : agentResponse);
    }

    private void markJournal(SideEffectJournal journal, String status, String rollbackResult) {
        journal.setStatus(status);
        journal.setExecutedAt(LocalDateTime.now());
        if (rollbackResult != null && !rollbackResult.isBlank()) {
            journal.setRollbackResult(rollbackResult);
        }
        sideEffectJournalRepository.save(journal);
    }

    private boolean verifyCompensation(String response, String originalAction) {
        if (response == null || response.isBlank()) {
            return false;
        }

        JsonNode verification = parseStructuredVerification(response);
        if (verification == null) {
            log.warn("Compensation verification response is not structured JSON for action {}", originalAction);
            return false;
        }

        String status = verification.path("status").asText("");
        if ("FAILED".equalsIgnoreCase(status)) {
            return false;
        }
        if (verification.has("verified")) {
            return verification.get("verified").asBoolean(false);
        }
        return "SUCCESS".equalsIgnoreCase(status);
    }

    private JsonNode parseStructuredVerification(String response) {
        String candidate = response.trim();
        if (candidate.startsWith("```")) {
            candidate = candidate.replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
        }
        if (!candidate.startsWith("{")) {
            int start = candidate.indexOf('{');
            int end = candidate.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            candidate = candidate.substring(start, end + 1);
        }

        try {
            JsonNode root = objectMapper.readTree(candidate);
            return root != null && root.isObject() ? root : null;
        } catch (Exception e) {
            log.warn("Failed to parse compensation verification JSON: {}", e.getMessage());
            return null;
        }
    }

    private String executeCompensationJson(TaskStep step, String compensationJson,
                                           TaskPlan plan, String userId) {
        try {
            JsonNode comp = objectMapper.readTree(compensationJson);
            if (comp.path("noOp").asBoolean(false)) {
                return "{\"status\":\"SUCCESS\",\"verified\":true,\"detail\":\"no compensation needed\"}";
            }
            if (comp.has("executable") && !comp.get("executable").asBoolean()) {
                log.warn("Compensation journal for step {} is not executable: {}",
                        step.getStepOrder(),
                        comp.has("reason") ? comp.get("reason").asText() : "manual review required");
                return null;
            }

            String action = comp.has("action") ? comp.get("action").asText() : null;
            if (action == null && comp.has("rollbackAction")) {
                action = comp.get("rollbackAction").asText();
            }
            String description = comp.has("description")
                    ? comp.get("description").asText()
                    : "Rollback operation";

            if (action == null) {
                log.warn("Compensation JSON has no action for step {}", step.getStepOrder());
                return null;
            }

            String prompt = String.format(
                    "Execute one rollback operation to undo a completed plan step.%n" +
                            "Original action: %s%n" +
                            "Rollback action: %s%n" +
                            "Description: %s%n" +
                            "Return JSON only, without markdown: " +
                            "{\"status\":\"SUCCESS|FAILED\",\"verified\":true|false,\"detail\":\"...\"}",
                    step.getAction(), action, description);

            var response = agentService.chatTrustedSystemPrompt(
                    prompt, List.of(), userId, userId,
                    "CompensationService", "json_rollback_prompt");
            log.info("Compensation executed for step {} via JSON: {}", step.getStepOrder(), action);
            return response.getContent();

        } catch (Exception e) {
            log.error("Failed to execute compensation JSON for step {}: {}",
                    step.getStepOrder(), e.getMessage());
            return null;
        }
    }
}
