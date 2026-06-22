package com.ainote.app.service;

import com.ainote.app.entity.TaskPlan;
import com.ainote.app.entity.TaskStep;
import com.ainote.app.repository.TaskPlanRepository;
import com.ainote.app.repository.TaskStepRepository;
import com.ainote.app.service.planning.CompensationService;
import com.ainote.app.service.planning.LlmTaskRouterService;
import com.ainote.app.service.planning.PlannerService;
import com.ainote.app.service.planning.PlanExecutor;
import com.ainote.app.service.planning.PlanStateValidator;
import com.ainote.app.service.planning.TaskRoute;
import com.ainote.app.service.planning.TaskRouteDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class PlanningAgentService {

    private static final Logger log = LoggerFactory.getLogger(PlanningAgentService.class);
    private static final int MAX_ACTIVE_PLANS = 5;

    private final LlmTaskRouterService taskRouter;
    private final AgentService agentService;
    private final PlannerService plannerService;
    private final PlanExecutor planExecutor;
    private final CompensationService compensationService;
    private final PlanStateValidator stateValidator;
    private final TaskPlanRepository planRepository;
    private final TaskStepRepository stepRepository;

    public PlanningAgentService(
            LlmTaskRouterService taskRouter,
            AgentService agentService,
            PlannerService plannerService,
            PlanExecutor planExecutor,
            CompensationService compensationService,
            PlanStateValidator stateValidator,
            TaskPlanRepository planRepository,
            TaskStepRepository stepRepository) {
        this.taskRouter = taskRouter;
        this.agentService = agentService;
        this.plannerService = plannerService;
        this.planExecutor = planExecutor;
        this.compensationService = compensationService;
        this.stateValidator = stateValidator;
        this.planRepository = planRepository;
        this.stepRepository = stepRepository;
    }

    public Map<String, Object> smartChat(String query, List<String> noteIds, String userId) {
        return smartChat(query, noteIds, userId, false);
    }

    public Map<String, Object> smartChat(String query, List<String> noteIds, String userId, boolean forcePlan) {
        TaskRouteDecision decision = forcePlan
                ? new TaskRouteDecision(TaskRoute.PLANNED_TASK, 1.0, "前端已完成路由判断，强制创建计划", true, 0, "MEDIUM")
                : taskRouter.route(query, noteIds);
        log.info("Task route: {} confidence={} reason={}",
                decision.route(), decision.confidence(), decision.reason());

        if (decision.route() == TaskRoute.DIRECT_AGENT) {
            var response = agentService.chat(query, noteIds, userId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "direct");
            result.put("route", decision.route().name());
            result.put("routeDecision", routeDecisionToMap(decision));
            result.put("content", response.getContent());
            result.put("sources", response.getSources());
            result.put("actionJson", response.getAction());
            return result;
        }

        long activeCount = planRepository.countByUserIdAndStatusIn(userId,
                List.of(TaskPlan.STATUS_EXECUTING, TaskPlan.STATUS_PAUSED, TaskPlan.STATUS_AWAITING_APPROVAL));
        if (activeCount >= MAX_ACTIVE_PLANS) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "direct");
            result.put("route", TaskRoute.DIRECT_AGENT.name());
            result.put("routeDecision", routeDecisionToMap(decision));
            result.put("content", "你当前有 " + activeCount + " 个活跃任务计划，请先完成或取消一些再创建新的。");
            return result;
        }

        TaskPlan plan = plannerService.generatePlan(query, noteIds, userId);
        List<TaskStep> steps = stepRepository.findByPlanIdOrderByStepOrder(plan.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "plan_created");
        result.put("route", decision.route().name());
        result.put("routeDecision", routeDecisionToMap(decision));
        result.put("plan", planToMap(plan, steps));
        return result;
    }

    @Async("taskExecutor")
    public void approvePlan(String planId, String userId) {
        getPlanForUser(planId, userId);
        planExecutor.execute(planId, userId);
    }

    public void pausePlan(String planId, String userId) {
        TaskPlan plan = getPlanForUser(planId, userId);
        if (TaskPlan.STATUS_EXECUTING.equals(plan.getStatus())) {
            plan.setStatus(TaskPlan.STATUS_PAUSED);
            planRepository.save(plan);
        }
    }

    @Async("taskExecutor")
    public void resumePlan(String planId, String userId) {
        TaskPlan plan = getPlanForUser(planId, userId);
        if (TaskPlan.STATUS_PAUSED.equals(plan.getStatus())
                || TaskPlan.STATUS_FAILED.equals(plan.getStatus())) {
            planExecutor.execute(planId, userId);
        }
    }

    public void cancelPlan(String planId, String userId) {
        TaskPlan plan = getPlanForUser(planId, userId);
        agentService.cancelCurrentRequest(userId);
        stateValidator.validateTransition(plan.getStatus(), TaskPlan.STATUS_CANCELLED);
        plan.setStatus(TaskPlan.STATUS_CANCELLED);
        planRepository.save(plan);
    }

    @Async("taskExecutor")
    public void rollbackPlan(String planId, String userId) {
        TaskPlan plan = getPlanForUser(planId, userId);
        if (!TaskPlan.STATUS_FAILED.equals(plan.getStatus())
                && !TaskPlan.STATUS_PAUSED.equals(plan.getStatus())
                && !TaskPlan.STATUS_EXECUTING.equals(plan.getStatus())) {
            throw new IllegalStateException("Cannot rollback plan in status: " + plan.getStatus());
        }
        compensationService.rollbackPlan(planId, userId);
    }

    public void skipStep(String planId, String stepId, String userId) {
        getPlanForUser(planId, userId);
        TaskStep step = getStepForPlan(stepId, planId);
        if (TaskStep.STATUS_PENDING.equals(step.getStatus())
                || TaskStep.STATUS_FAILED.equals(step.getStatus())) {
            step.setStatus(TaskStep.STATUS_SKIPPED);
            stepRepository.save(step);
        }
    }

    public void modifyStep(String planId, String stepId, String newParams, String userId) {
        getPlanForUser(planId, userId);
        TaskStep step = getStepForPlan(stepId, planId);
        if (TaskStep.STATUS_PENDING.equals(step.getStatus())) {
            step.setInputParams(newParams);
            stepRepository.save(step);
        }
    }

    private TaskStep getStepForPlan(String stepId, String planId) {
        return stepRepository.findByIdAndPlanId(stepId, planId)
                .orElseThrow(() -> new NoSuchElementException("Step not found"));
    }

    public TaskPlan getPlanForUser(String planId, String userId) {
        TaskPlan plan = planRepository.findById(planId).orElseThrow();
        if (!plan.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized access to plan");
        }
        return plan;
    }

    public List<TaskPlan> listPlans(String userId) {
        return planRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Map<String, Object> getPlanDetail(String planId, String userId) {
        TaskPlan plan = getPlanForUser(planId, userId);
        List<TaskStep> steps = stepRepository.findByPlanIdOrderByStepOrder(planId);
        return planToMap(plan, steps);
    }

    private Map<String, Object> routeDecisionToMap(TaskRouteDecision decision) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("route", decision.route().name());
        map.put("confidence", decision.confidence());
        map.put("reason", decision.reason());
        map.put("requiresUserPlanApproval", decision.requiresUserPlanApproval());
        map.put("estimatedToolSteps", decision.estimatedToolSteps());
        map.put("riskLevel", decision.riskLevel());
        return map;
    }

    private Map<String, Object> planToMap(TaskPlan plan, List<TaskStep> steps) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", plan.getId());
        map.put("goal", plan.getGoal());
        map.put("status", plan.getStatus());
        map.put("originalQuery", plan.getOriginalQuery());
        map.put("totalSteps", plan.getTotalSteps());
        map.put("completedSteps", plan.getCompletedSteps());
        map.put("createdAt", plan.getCreatedAt());
        map.put("updatedAt", plan.getUpdatedAt());
        map.put("steps", steps.stream().map(s -> {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("id", s.getId());
            sm.put("order", s.getStepOrder());
            sm.put("action", s.getAction());
            sm.put("description", s.getDescription());
            sm.put("status", s.getStatus());
            sm.put("inputParams", s.getInputParams());
            sm.put("outputResult", s.getOutputResult());
            sm.put("retryCount", s.getRetryCount());
            sm.put("errorMessage", s.getErrorMessage());
            return sm;
        }).toList());
        return map;
    }
}
