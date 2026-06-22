package com.ainote.app.service.planning;

import com.ainote.app.entity.TaskPlan;
import com.ainote.app.entity.TaskStep;
import com.ainote.app.model.AiChatResponse;
import com.ainote.app.repository.TaskPlanRepository;
import com.ainote.app.repository.TaskStepRepository;
import com.ainote.app.repository.UserMemoryRepository;
import com.ainote.app.service.AgentMetricsService;
import com.ainote.app.service.AgentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlanExecutor dependency graph tests")
class PlanExecutorDependencyGraphTest {

    @Mock private AgentService agentService;
    @Mock private PlannerService plannerService;
    @Mock private ReflectionService reflectionService;
    @Mock private StepParamResolver paramResolver;
    @Mock private PlanProgressEmitter progressEmitter;
    @Mock private TaskPlanRepository planRepository;
    @Mock private TaskStepRepository stepRepository;
    @Mock private PlanStepCompletionRecorder stepCompletionRecorder;
    @Mock private UserMemoryRepository userMemoryRepository;
    @Mock private ExecutorService securityExecutor;
    @Mock private AgentMetricsService metricsService;

    private PlanExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new PlanExecutor(
                agentService, plannerService, reflectionService,
                paramResolver, progressEmitter, planRepository,
                stepRepository, stepCompletionRecorder,
                userMemoryRepository, new ObjectMapper(),
                securityExecutor, metricsService);
        ReflectionTestUtils.setField(executor, "maxRetries", 3);
    }

    @Test
    void buildExecutionWaves_rejectsCyclicDependencies() {
        TaskStep first = step("plan-1", 1, TaskStep.STATUS_PENDING, 2);
        TaskStep second = step("plan-1", 2, TaskStep.STATUS_PENDING, 1);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                executor,
                "buildExecutionWaves",
                List.of(first, second),
                new HashMap<Integer, TaskStep>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsatisfied or cyclic step dependencies");
    }

    @Test
    void buildExecutionWaves_rejectsMissingDependencies() {
        TaskStep step = step("plan-1", 2, TaskStep.STATUS_PENDING, 99);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                executor,
                "buildExecutionWaves",
                List.of(step),
                new HashMap<Integer, TaskStep>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsatisfied or cyclic step dependencies");
    }

    @Test
    void execute_marksPlanFailedWhenWaveConstructionFails() {
        String planId = "plan-1";
        String userId = "user-1";
        TaskPlan plan = plan(planId);
        TaskStep first = step(planId, 1, TaskStep.STATUS_PENDING, 2);
        TaskStep second = step(planId, 2, TaskStep.STATUS_PENDING, 1);

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(stepRepository.findByPlanIdOrderByStepOrder(planId)).thenReturn(List.of(first, second));

        executor.execute(planId, userId);

        verify(planRepository, atLeastOnce()).save(argThat(saved ->
                TaskPlan.STATUS_FAILED.equals(saved.getStatus())
                        && saved.getErrorMessage() != null
                        && saved.getErrorMessage().contains("Unsatisfied or cyclic step dependencies")));
        verify(metricsService).recordPlanFailed();
    }

    @Test
    void collectExecutableSteps_blocksStepsWithUnmetDependencies() {
        TaskStep blocked = step("plan-1", 2, TaskStep.STATUS_PENDING, 1);

        @SuppressWarnings("unchecked")
        List<TaskStep> executable = ReflectionTestUtils.invokeMethod(
                executor,
                "collectExecutableSteps",
                List.of(blocked),
                new HashMap<Integer, TaskStep>());

        assertThat(executable).isEmpty();
        verify(stepRepository).save(argThat(saved ->
                saved.getStepOrder() == 2
                        && TaskStep.STATUS_BLOCKED.equals(saved.getStatus())));
    }

    @Test
    void execute_runsIndependentWaveSequentiallyWithoutSecurityExecutor() {
        String planId = "plan-1";
        String userId = "user-1";
        TaskPlan plan = plan(planId);
        TaskStep first = step(planId, 1, TaskStep.STATUS_PENDING);
        TaskStep second = step(planId, 2, TaskStep.STATUS_PENDING);
        CountingExecutorService countingExecutor = new CountingExecutorService();
        executor = new PlanExecutor(
                agentService, plannerService, reflectionService,
                paramResolver, progressEmitter, planRepository,
                stepRepository, stepCompletionRecorder,
                userMemoryRepository, new ObjectMapper(),
                countingExecutor, metricsService);
        ReflectionTestUtils.setField(executor, "maxRetries", 3);

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(stepRepository.findByPlanIdOrderByStepOrder(planId)).thenReturn(List.of(first, second));
        when(paramResolver.resolve(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn("{}");
        when(agentService.chatTrustedSystemPrompt(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new AiChatResponse("ok", new HashMap<>()));
        when(reflectionService.reflect(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("ok"), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(ReflectionService.Decision.CONTINUE);

        executor.execute(planId, userId);

        assertThat(countingExecutor.executions).isZero();
        verify(agentService, org.mockito.Mockito.times(2)).chatTrustedSystemPrompt(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void execute_doesNotSaveStepSuccessWhenPlanCancelledAfterAgentReturns() {
        String planId = "plan-1";
        String userId = "user-1";
        TaskPlan executingPlan = plan(planId);
        TaskPlan cancelledPlan = plan(planId);
        cancelledPlan.setStatus(TaskPlan.STATUS_CANCELLED);
        TaskStep step = step(planId, 1, TaskStep.STATUS_PENDING);

        when(planRepository.findById(planId))
                .thenReturn(Optional.of(executingPlan))
                .thenReturn(Optional.of(executingPlan))
                .thenReturn(Optional.of(executingPlan))
                .thenReturn(Optional.of(cancelledPlan))
                .thenReturn(Optional.of(cancelledPlan));
        when(stepRepository.findByPlanIdOrderByStepOrder(planId)).thenReturn(List.of(step));
        when(paramResolver.resolve(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn("{}");
        when(agentService.chatTrustedSystemPrompt(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new AiChatResponse("ok", new HashMap<>()));

        executor.execute(planId, userId);

        verify(reflectionService, never()).reflect(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(stepRepository, never()).save(argThat(saved ->
                saved.getStepOrder() == 1
                        && TaskStep.STATUS_SUCCESS.equals(saved.getStatus())));
    }

    @Test
    void execute_recordsSideEffectJournalForSuccessfulMutatingStep() {
        String planId = "plan-1";
        String userId = "user-1";
        TaskPlan plan = plan(planId);
        TaskStep step = step(planId, 1, TaskStep.STATUS_PENDING);
        step.setAction("create_note");

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(stepRepository.findByPlanIdOrderByStepOrder(planId)).thenReturn(List.of(step));
        when(paramResolver.resolve(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn("{}");
        when(agentService.chatTrustedSystemPrompt(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new AiChatResponse(
                        "Created note ID: 123e4567-e89b-12d3-a456-426614174000",
                        new HashMap<>()));
        when(reflectionService.reflect(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(ReflectionService.Decision.CONTINUE);

        executor.execute(planId, userId);

        verify(stepCompletionRecorder).recordSuccess(
                argThat(saved -> TaskStep.STATUS_SUCCESS.equals(saved.getStatus())
                        && saved.getCompensation() == null),
                argThat(journal -> journal.isPresent()
                        && planId.equals(journal.get().getPlanId())
                        && "step-1".equals(journal.get().getStepId())
                        && Integer.valueOf(1).equals(journal.get().getStepOrder())
                        && Integer.valueOf(1).equals(journal.get().getVersion())
                        && "create_note".equals(journal.get().getOriginalAction())
                        && "DELETE_CREATED_RESOURCE".equals(journal.get().getRollbackAction())
                        && "123e4567-e89b-12d3-a456-426614174000".equals(journal.get().getResourceId())
                        && Boolean.TRUE.equals(journal.get().getExecutable())
                        && journal.get().getOutputSnapshot() != null
                        && journal.get().getJournalJson() != null
                        && journal.get().getJournalJson().contains("\"type\":\"SIDE_EFFECT_JOURNAL\"")));
        verify(stepRepository, never()).save(argThat(saved ->
                TaskStep.STATUS_SUCCESS.equals(saved.getStatus())
                        && saved.getCompensation() != null));
    }

    @Test
    void buildOutputResult_fallbackStillProducesValidJsonWhenSerializationFails() throws Exception {
        PlanExecutor executorWithFailingMapper = new PlanExecutor(
                agentService, plannerService, reflectionService,
                paramResolver, progressEmitter, planRepository,
                stepRepository, stepCompletionRecorder,
                userMemoryRepository, new FailingWriteObjectMapper(),
                securityExecutor, metricsService);
        String response = "line1\nline2 with quote \" and slash \\";

        String output = ReflectionTestUtils.invokeMethod(
                executorWithFailingMapper,
                "buildOutputResult",
                response);

        assertThat(new ObjectMapper().readTree(output).get("response").asText())
                .isEqualTo(response);
    }

    private static class CountingExecutorService extends AbstractExecutorService {
        int executions;
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            executions++;
            command.run();
        }
    }

    private static class FailingWriteObjectMapper extends ObjectMapper {
        @Override
        public String writeValueAsString(Object value) throws JsonProcessingException {
            throw new JsonProcessingException("forced serialization failure") {};
        }
    }

    private TaskPlan plan(String planId) {
        TaskPlan plan = new TaskPlan();
        plan.setId(planId);
        plan.setUserId("user-1");
        plan.setStatus(TaskPlan.STATUS_AWAITING_APPROVAL);
        plan.setGoal("test goal");
        plan.setTotalSteps(2);
        plan.setCompletedSteps(0);
        return plan;
    }

    private TaskStep step(String planId, int order, String status, Integer... dependsOn) {
        TaskStep step = new TaskStep();
        step.setId("step-" + order);
        step.setPlanId(planId);
        step.setStepOrder(order);
        step.setAction("test_action");
        step.setDescription("test step " + order);
        step.setStatus(status);
        step.setDependsOn(dependsOn.length == 0 ? null : dependsOn);
        step.setRetryCount(0);
        return step;
    }
}
