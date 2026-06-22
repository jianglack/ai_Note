package com.ainote.app.service;

import com.ainote.app.entity.TaskPlan;
import com.ainote.app.entity.TaskStep;
import com.ainote.app.model.AiChatResponse;
import com.ainote.app.repository.TaskPlanRepository;
import com.ainote.app.repository.TaskStepRepository;
import com.ainote.app.service.planning.CompensationService;
import com.ainote.app.service.planning.LlmTaskRouterService;
import com.ainote.app.service.planning.PlanExecutor;
import com.ainote.app.service.planning.PlanStateValidator;
import com.ainote.app.service.planning.PlannerService;
import com.ainote.app.service.planning.TaskRoute;
import com.ainote.app.service.planning.TaskRouteDecision;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanningAgentServiceTest {

    @Test
    void directRouteDelegatesToAgentService() {
        LlmTaskRouterService router = mock(LlmTaskRouterService.class);
        AgentService agentService = mock(AgentService.class);
        PlannerService plannerService = mock(PlannerService.class);
        TaskPlanRepository planRepository = mock(TaskPlanRepository.class);
        TaskStepRepository stepRepository = mock(TaskStepRepository.class);

        when(router.route(anyString(), anyList())).thenReturn(new TaskRouteDecision(
                TaskRoute.DIRECT_AGENT, 0.91, "一次 Agent 对话即可完成", false, 2, "LOW"));
        when(agentService.chat("查找 RAG 相关笔记并总结一下。", List.of(), "user-1"))
                .thenReturn(new AiChatResponse("总结结果", new HashMap<>(), (String) null));

        PlanningAgentService service = service(router, agentService, plannerService, planRepository, stepRepository);

        Map<String, Object> result = service.smartChat("查找 RAG 相关笔记并总结一下。", List.of(), "user-1");

        assertThat(result.get("type")).isEqualTo("direct");
        assertThat(result.get("route")).isEqualTo("DIRECT_AGENT");
        assertThat(result.get("content")).isEqualTo("总结结果");
        verify(plannerService, never()).generatePlan(anyString(), anyList(), anyString());
    }

    @Test
    void plannedRouteCreatesTaskPlan() {
        LlmTaskRouterService router = mock(LlmTaskRouterService.class);
        AgentService agentService = mock(AgentService.class);
        PlannerService plannerService = mock(PlannerService.class);
        TaskPlanRepository planRepository = mock(TaskPlanRepository.class);
        TaskStepRepository stepRepository = mock(TaskStepRepository.class);
        TaskPlan plan = plan("plan-1", "整理毕业论文素材", "复杂请求");

        when(router.route(anyString(), anyList())).thenReturn(new TaskRouteDecision(
                TaskRoute.PLANNED_TASK, 0.98, "需要计划审批", true, 5, "MEDIUM"));
        when(planRepository.countByUserIdAndStatusIn(anyString(), anyList())).thenReturn(0L);
        when(plannerService.generatePlan("复杂请求", List.of(), "user-1")).thenReturn(plan);
        when(stepRepository.findByPlanIdOrderByStepOrder("plan-1")).thenReturn(List.of());

        PlanningAgentService service = service(router, agentService, plannerService, planRepository, stepRepository);

        Map<String, Object> result = service.smartChat("复杂请求", List.of(), "user-1");

        assertThat(result.get("type")).isEqualTo("plan_created");
        assertThat(result.get("route")).isEqualTo("PLANNED_TASK");
        assertThat(result.get("routeDecision")).isInstanceOf(Map.class);
        assertThat(result.get("plan")).isInstanceOf(Map.class);
        verify(agentService, never()).chat(anyString(), anyList(), anyString());
    }

    @Test
    void forcePlanSkipsRouterAndCreatesPlan() {
        LlmTaskRouterService router = mock(LlmTaskRouterService.class);
        AgentService agentService = mock(AgentService.class);
        PlannerService plannerService = mock(PlannerService.class);
        TaskPlanRepository planRepository = mock(TaskPlanRepository.class);
        TaskStepRepository stepRepository = mock(TaskStepRepository.class);
        TaskPlan plan = plan("plan-force", "强制计划", "复杂请求");

        when(planRepository.countByUserIdAndStatusIn(anyString(), anyList())).thenReturn(0L);
        when(plannerService.generatePlan("复杂请求", List.of(), "user-1")).thenReturn(plan);
        when(stepRepository.findByPlanIdOrderByStepOrder("plan-force")).thenReturn(List.of());

        PlanningAgentService service = service(router, agentService, plannerService, planRepository, stepRepository);

        Map<String, Object> result = service.smartChat("复杂请求", List.of(), "user-1", true);

        assertThat(result.get("type")).isEqualTo("plan_created");
        assertThat(result.get("route")).isEqualTo("PLANNED_TASK");
        verify(router, never()).route(anyString(), anyList());
    }

    @Test
    void approvePlan_rejectsPlanOutsideCurrentUserBeforeExecuting() {
        LlmTaskRouterService router = mock(LlmTaskRouterService.class);
        AgentService agentService = mock(AgentService.class);
        PlannerService plannerService = mock(PlannerService.class);
        PlanExecutor planExecutor = mock(PlanExecutor.class);
        TaskPlanRepository planRepository = mock(TaskPlanRepository.class);
        TaskStepRepository stepRepository = mock(TaskStepRepository.class);
        TaskPlan foreignPlan = plan("plan-2", "goal", "query");
        foreignPlan.setUserId("user-2");

        when(planRepository.findById("plan-2")).thenReturn(Optional.of(foreignPlan));

        PlanningAgentService service = new PlanningAgentService(
                router,
                agentService,
                plannerService,
                planExecutor,
                mock(CompensationService.class),
                mock(PlanStateValidator.class),
                planRepository,
                stepRepository
        );

        assertThatThrownBy(() -> service.approvePlan("plan-2", "user-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unauthorized access to plan");

        verify(planExecutor, never()).execute("plan-2", "user-1");
    }

    @Test
    void cancelPlan_cancelsCurrentAgentRequestBeforeSavingCancelledStatus() {
        LlmTaskRouterService router = mock(LlmTaskRouterService.class);
        AgentService agentService = mock(AgentService.class);
        PlannerService plannerService = mock(PlannerService.class);
        TaskPlanRepository planRepository = mock(TaskPlanRepository.class);
        TaskStepRepository stepRepository = mock(TaskStepRepository.class);
        PlanStateValidator stateValidator = mock(PlanStateValidator.class);
        TaskPlan plan = plan("plan-1", "goal", "query");
        plan.setStatus(TaskPlan.STATUS_EXECUTING);

        when(planRepository.findById("plan-1")).thenReturn(Optional.of(plan));

        PlanningAgentService service = new PlanningAgentService(
                router,
                agentService,
                plannerService,
                mock(PlanExecutor.class),
                mock(CompensationService.class),
                stateValidator,
                planRepository,
                stepRepository
        );

        service.cancelPlan("plan-1", "user-1");

        verify(agentService).cancelCurrentRequest("user-1");
        verify(planRepository).save(argThat(saved ->
                "plan-1".equals(saved.getId())
                        && TaskPlan.STATUS_CANCELLED.equals(saved.getStatus())));
    }

    @Test
    void skipStep_skipsOnlyStepThatBelongsToPlan() {
        LlmTaskRouterService router = mock(LlmTaskRouterService.class);
        AgentService agentService = mock(AgentService.class);
        PlannerService plannerService = mock(PlannerService.class);
        TaskPlanRepository planRepository = mock(TaskPlanRepository.class);
        TaskStepRepository stepRepository = mock(TaskStepRepository.class);
        TaskPlan plan = plan("plan-1", "goal", "query");
        TaskStep step = step("step-1", "plan-1", TaskStep.STATUS_PENDING);

        when(planRepository.findById("plan-1")).thenReturn(Optional.of(plan));
        when(stepRepository.findByIdAndPlanId("step-1", "plan-1")).thenReturn(Optional.of(step));

        PlanningAgentService service = service(router, agentService, plannerService, planRepository, stepRepository);

        service.skipStep("plan-1", "step-1", "user-1");

        verify(stepRepository).findByIdAndPlanId("step-1", "plan-1");
        verify(stepRepository).save(argThat(saved ->
                "step-1".equals(saved.getId())
                        && TaskStep.STATUS_SKIPPED.equals(saved.getStatus())));
    }

    @Test
    void skipStep_rejectsStepOutsidePlanWithGenericNotFound() {
        LlmTaskRouterService router = mock(LlmTaskRouterService.class);
        AgentService agentService = mock(AgentService.class);
        PlannerService plannerService = mock(PlannerService.class);
        TaskPlanRepository planRepository = mock(TaskPlanRepository.class);
        TaskStepRepository stepRepository = mock(TaskStepRepository.class);
        TaskPlan plan = plan("plan-1", "goal", "query");

        when(planRepository.findById("plan-1")).thenReturn(Optional.of(plan));
        when(stepRepository.findByIdAndPlanId("foreign-step", "plan-1")).thenReturn(Optional.empty());

        PlanningAgentService service = service(router, agentService, plannerService, planRepository, stepRepository);

        assertThatThrownBy(() -> service.skipStep("plan-1", "foreign-step", "user-1"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Step not found");
        verify(stepRepository, never()).save(argThat(saved -> "foreign-step".equals(saved.getId())));
    }

    @Test
    void modifyStep_modifiesOnlyStepThatBelongsToPlan() {
        LlmTaskRouterService router = mock(LlmTaskRouterService.class);
        AgentService agentService = mock(AgentService.class);
        PlannerService plannerService = mock(PlannerService.class);
        TaskPlanRepository planRepository = mock(TaskPlanRepository.class);
        TaskStepRepository stepRepository = mock(TaskStepRepository.class);
        TaskPlan plan = plan("plan-1", "goal", "query");
        TaskStep step = step("step-1", "plan-1", TaskStep.STATUS_PENDING);

        when(planRepository.findById("plan-1")).thenReturn(Optional.of(plan));
        when(stepRepository.findByIdAndPlanId("step-1", "plan-1")).thenReturn(Optional.of(step));

        PlanningAgentService service = service(router, agentService, plannerService, planRepository, stepRepository);

        service.modifyStep("plan-1", "step-1", "{\"title\":\"updated\"}", "user-1");

        verify(stepRepository).findByIdAndPlanId("step-1", "plan-1");
        verify(stepRepository).save(argThat(saved ->
                "step-1".equals(saved.getId())
                        && "{\"title\":\"updated\"}".equals(saved.getInputParams())));
    }

    @Test
    void modifyStep_rejectsStepOutsidePlanWithGenericNotFound() {
        LlmTaskRouterService router = mock(LlmTaskRouterService.class);
        AgentService agentService = mock(AgentService.class);
        PlannerService plannerService = mock(PlannerService.class);
        TaskPlanRepository planRepository = mock(TaskPlanRepository.class);
        TaskStepRepository stepRepository = mock(TaskStepRepository.class);
        TaskPlan plan = plan("plan-1", "goal", "query");

        when(planRepository.findById("plan-1")).thenReturn(Optional.of(plan));
        when(stepRepository.findByIdAndPlanId("foreign-step", "plan-1")).thenReturn(Optional.empty());

        PlanningAgentService service = service(router, agentService, plannerService, planRepository, stepRepository);

        assertThatThrownBy(() -> service.modifyStep("plan-1", "foreign-step", "{}", "user-1"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Step not found");
        verify(stepRepository, never()).save(argThat(saved -> "foreign-step".equals(saved.getId())));
    }

    private PlanningAgentService service(
            LlmTaskRouterService router,
            AgentService agentService,
            PlannerService plannerService,
            TaskPlanRepository planRepository,
            TaskStepRepository stepRepository
    ) {
        return new PlanningAgentService(
                router,
                agentService,
                plannerService,
                mock(PlanExecutor.class),
                mock(CompensationService.class),
                mock(PlanStateValidator.class),
                planRepository,
                stepRepository
        );
    }

    private TaskPlan plan(String id, String goal, String query) {
        TaskPlan plan = new TaskPlan();
        plan.setId(id);
        plan.setUserId("user-1");
        plan.setGoal(goal);
        plan.setOriginalQuery(query);
        plan.setStatus(TaskPlan.STATUS_AWAITING_APPROVAL);
        plan.setTotalSteps(0);
        plan.setCompletedSteps(0);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        return plan;
    }

    private TaskStep step(String id, String planId, String status) {
        TaskStep step = new TaskStep();
        step.setId(id);
        step.setPlanId(planId);
        step.setStepOrder(1);
        step.setAction("test_action");
        step.setDescription("test step");
        step.setStatus(status);
        step.setInputParams("{}");
        step.setRetryCount(0);
        return step;
    }
}
