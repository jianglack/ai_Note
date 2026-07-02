package com.ainote.app.service.planning;

import com.ainote.app.entity.TaskPlan;
import com.ainote.app.entity.TaskStep;
import com.ainote.app.model.AiChatResponse;
import com.ainote.app.repository.TaskPlanRepository;
import com.ainote.app.repository.TaskStepRepository;
import com.ainote.app.repository.UserMemoryRepository;
import com.ainote.app.service.AgentMetricsService;
import com.ainote.app.service.AgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
@DisplayName("PlanExecutor recursion depth limit tests")
class PlanExecutorRecursionTest {
    private AgentService agentService;
    private PlannerService plannerService;
    private ReflectionService reflectionService;
    private StepParamResolver paramResolver;
    private PlanProgressEmitter progressEmitter;
    private TaskPlanRepository planRepository;
    private TaskStepRepository stepRepository;
    private PlanStepCompletionRecorder stepCompletionRecorder;
    private UserMemoryRepository userMemoryRepository;
    private ExecutorService securityExecutor;
    private AgentMetricsService metricsService;
    private PlanExecutor executor;

    @BeforeEach
    void setUp() {
        agentService = mock(AgentService.class);
        plannerService = mock(PlannerService.class);
        reflectionService = mock(ReflectionService.class);
        paramResolver = mock(StepParamResolver.class);
        progressEmitter = mock(PlanProgressEmitter.class);
        planRepository = mock(TaskPlanRepository.class);
        stepRepository = mock(TaskStepRepository.class);
        stepCompletionRecorder = mock(PlanStepCompletionRecorder.class);
        userMemoryRepository = mock(UserMemoryRepository.class);
        securityExecutor = mock(ExecutorService.class);
        metricsService = mock(AgentMetricsService.class);
        executor = new PlanExecutor(
                agentService, plannerService, reflectionService,
                paramResolver, progressEmitter, planRepository,
                stepRepository, stepCompletionRecorder,
                userMemoryRepository, new ObjectMapper(),
                securityExecutor, metricsService);
        ReflectionTestUtils.setField(executor, "maxRetries", 3);
    }

    @Test
    @DisplayName("continuous INSERT_STEP beyond max depth marks plan failed")
    void execute_exceedsMaxDepth_planFails() {
        String planId = "plan-1";
        String userId = "user-1";

        TaskPlan plan = createPlan(planId);
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        when(stepRepository.findByPlanIdOrderByStepOrder(planId))
                .thenAnswer(invocation -> List.of(createStep(planId, 1)));

        when(agentService.chatTrustedSystemPrompt(
                anyString(), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new AiChatResponse("need prerequisite", new HashMap<>()));
        when(reflectionService.reflect(any(), anyString(), any()))
                .thenReturn(ReflectionService.Decision.INSERT_STEP);
        when(paramResolver.resolve(any(), any())).thenReturn("{}");

        executor.execute(planId, userId);

        verify(planRepository, atLeastOnce()).save(argThat(p ->
                TaskPlan.STATUS_FAILED.equals(p.getStatus())
                        && p.getErrorMessage() != null
                        && p.getErrorMessage().contains("recursion depth")));
        verify(stepRepository, atLeastOnce()).save(argThat(s ->
                "AUTO_PREREQUISITE".equals(s.getAction())));
    }

    @Test
    @DisplayName("Path A wrapper error retry exceeds max retries")
    void executeStep_wrapperErrorRetryExceedsMax_stepFails() {
        String planId = "plan-2";
        String userId = "user-1";

        TaskPlan plan = createPlan(planId);
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        TaskStep step = createStep(planId, 1);
        when(stepRepository.findByPlanIdOrderByStepOrder(planId))
                .thenReturn(List.of(step));

        when(agentService.chatTrustedSystemPrompt(
                anyString(), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new AiChatResponse("抱歉，处理时出错", new HashMap<>()));
        when(reflectionService.reflect(any(), anyString(), any()))
                .thenReturn(ReflectionService.Decision.RETRY);
        when(paramResolver.resolve(any(), any())).thenReturn("{}");

        executor.execute(planId, userId);

        verify(stepRepository, atLeastOnce()).save(argThat(s ->
                TaskStep.STATUS_FAILED.equals(s.getStatus())));
        verify(agentService, times(4)).chatTrustedSystemPrompt(
                anyString(), anyList(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Path B normal RETRY exceeds max retries")
    void executeStep_normalRetryExceedsMax_stepFails() {
        String planId = "plan-2b";
        String userId = "user-1";

        TaskPlan plan = createPlan(planId);
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        TaskStep step = createStep(planId, 1);
        when(stepRepository.findByPlanIdOrderByStepOrder(planId))
                .thenReturn(List.of(step));

        when(agentService.chatTrustedSystemPrompt(
                anyString(), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new AiChatResponse("needs retry", new HashMap<>()));
        when(reflectionService.reflect(any(), eq("needs retry"), isNull()))
                .thenReturn(ReflectionService.Decision.RETRY);
        when(paramResolver.resolve(any(), any())).thenReturn("{}");

        executor.execute(planId, userId);

        verify(stepRepository, atLeastOnce()).save(argThat(s ->
                TaskStep.STATUS_FAILED.equals(s.getStatus())
                        && "Max retries exceeded".equals(s.getErrorMessage())));
        verify(agentService, times(4)).chatTrustedSystemPrompt(
                anyString(), anyList(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Path C exception retry exceeds max retries")
    void executeStep_exceptionRetryExceedsMax_stepFails() {
        String planId = "plan-3";
        String userId = "user-1";

        TaskPlan plan = createPlan(planId);
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        TaskStep step = createStep(planId, 1);
        when(stepRepository.findByPlanIdOrderByStepOrder(planId))
                .thenReturn(List.of(step));

        when(agentService.chatTrustedSystemPrompt(
                anyString(), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("API error"));
        when(reflectionService.reflect(any(), isNull(), any()))
                .thenReturn(ReflectionService.Decision.RETRY);
        when(paramResolver.resolve(any(), any())).thenReturn("{}");

        executor.execute(planId, userId);

        verify(stepRepository, atLeastOnce()).save(argThat(s ->
                TaskStep.STATUS_FAILED.equals(s.getStatus())));
        verify(agentService, times(4)).chatTrustedSystemPrompt(
                anyString(), anyList(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("handleStepResult returns false when plan is already failed")
    void handleStepResult_planAlreadyFailed_returnsFalse() {
        String planId = "plan-4";

        TaskPlan originalPlan = createPlan(planId);

        TaskPlan failedPlan = createPlan(planId);
        failedPlan.setStatus(TaskPlan.STATUS_FAILED);
        when(planRepository.findById(planId)).thenReturn(Optional.of(failedPlan));

        TaskStep successStep = createStep(planId, 2);
        successStep.setStatus(TaskStep.STATUS_SUCCESS);

        Map<Integer, TaskStep> completedSteps = new LinkedHashMap<>();

        Boolean result = ReflectionTestUtils.invokeMethod(
                executor, "handleStepResult",
                successStep, originalPlan, completedSteps);

        assertThat(result).isFalse();
        assertThat(completedSteps).isEmpty();
        verify(metricsService, never()).recordStepExecution(true);
        verify(planRepository, never()).save(argThat(p ->
                TaskPlan.STATUS_COMPLETED.equals(p.getStatus())));
    }

    private TaskPlan createPlan(String planId) {
        TaskPlan plan = new TaskPlan();
        plan.setId(planId);
        plan.setStatus(TaskPlan.STATUS_EXECUTING);
        plan.setGoal("test goal");
        plan.setTotalSteps(1);
        plan.setCompletedSteps(0);
        return plan;
    }

    private TaskStep createStep(String planId, int order) {
        TaskStep step = new TaskStep();
        step.setPlanId(planId);
        step.setStepOrder(order);
        step.setAction("test_action");
        step.setDescription("test step " + order);
        step.setStatus(TaskStep.STATUS_PENDING);
        step.setRetryCount(0);
        return step;
    }
}
