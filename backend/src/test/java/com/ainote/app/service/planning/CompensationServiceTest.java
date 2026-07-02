package com.ainote.app.service.planning;

import com.ainote.app.entity.TaskPlan;
import com.ainote.app.entity.SideEffectJournal;
import com.ainote.app.entity.TaskStep;
import com.ainote.app.model.AiChatResponse;
import com.ainote.app.repository.TaskPlanRepository;
import com.ainote.app.repository.SideEffectJournalRepository;
import com.ainote.app.repository.TaskStepRepository;
import com.ainote.app.service.AgentMetricsService;
import com.ainote.app.service.AgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
class CompensationServiceTest {

    private static final Path SOURCE_FILE = Path.of(
            "src", "main", "java", "com", "ainote", "app", "service", "planning", "CompensationService.java");
    private AgentService agentService;
    private TaskPlanRepository planRepository;
    private TaskStepRepository stepRepository;
    private SideEffectJournalRepository sideEffectJournalRepository;
    private AgentMetricsService metricsService;
    @BeforeEach
    void setUpMocks() {
        agentService = mock(AgentService.class);
        planRepository = mock(TaskPlanRepository.class);
        stepRepository = mock(TaskStepRepository.class);
        sideEffectJournalRepository = mock(SideEffectJournalRepository.class);
        metricsService = mock(AgentMetricsService.class);
    }

    @Test
    void rollbackPlan_doesNotInferCompensationWhenJournalIsMissing() {
        String planId = "plan-1";
        TaskPlan plan = new TaskPlan();
        plan.setId(planId);
        plan.setUserId("user-1");
        plan.setStatus(TaskPlan.STATUS_FAILED);

        TaskStep step = new TaskStep();
        step.setId("step-1");
        step.setPlanId(planId);
        step.setStepOrder(1);
        step.setAction("create_note");
        step.setStatus(TaskStep.STATUS_SUCCESS);
        step.setCompensation(null);

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(stepRepository.findByPlanIdAndStatusOrderByStepOrder(planId, TaskStep.STATUS_SUCCESS))
                .thenReturn(List.of(step));
        when(sideEffectJournalRepository.findByPlanIdAndStepIdOrderByCreatedAtDesc(planId, "step-1"))
                .thenReturn(List.of());
        when(stepRepository.findByPlanIdAndStatusOrderByStepOrder(planId, TaskStep.STATUS_PENDING))
                .thenReturn(List.of());

        CompensationService service = new CompensationService(
                agentService,
                planRepository,
                stepRepository,
                sideEffectJournalRepository,
                new ObjectMapper(),
                metricsService);

        service.rollbackPlan(planId, "user-1");

        verify(agentService, never()).chatTrustedSystemPrompt(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(stepRepository).save(argThat(saved ->
                TaskStep.STATUS_SUCCESS.equals(saved.getStatus())
                        && saved.getErrorMessage() != null
                        && saved.getErrorMessage().contains("missing compensation journal")));
        verify(planRepository).save(argThat(saved ->
                TaskPlan.STATUS_CANCELLED_PARTIAL.equals(saved.getStatus())));
    }

    @Test
    void rollbackPlan_usesIndependentJournalBeforeLegacyCompensationField() {
        String planId = "plan-1";
        TaskPlan plan = new TaskPlan();
        plan.setId(planId);
        plan.setUserId("user-1");
        plan.setStatus(TaskPlan.STATUS_FAILED);

        TaskStep step = new TaskStep();
        step.setId("step-1");
        step.setPlanId(planId);
        step.setStepOrder(1);
        step.setAction("create_note");
        step.setStatus(TaskStep.STATUS_SUCCESS);
        step.setCompensation(null);

        SideEffectJournal journal = new SideEffectJournal();
        journal.setId("journal-1");
        journal.setPlanId(planId);
        journal.setStepId("step-1");
        journal.setStepOrder(1);
        journal.setVersion(1);
        journal.setOriginalAction("create_note");
        journal.setRollbackAction("DELETE_CREATED_RESOURCE");
        journal.setResourceId("note-1");
        journal.setExecutable(true);
        journal.setStatus(SideEffectJournal.STATUS_PENDING);
        journal.setOutputSnapshot("{\"id\":\"note-1\"}");
        journal.setJournalJson("""
                {"type":"SIDE_EFFECT_JOURNAL","rollbackAction":"DELETE_CREATED_RESOURCE","resourceId":"note-1","executable":true,"description":"delete created note"}
                """);

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(stepRepository.findByPlanIdAndStatusOrderByStepOrder(planId, TaskStep.STATUS_SUCCESS))
                .thenReturn(List.of(step));
        when(sideEffectJournalRepository.findByPlanIdAndStepIdOrderByCreatedAtDesc(planId, "step-1"))
                .thenReturn(List.of(journal));
        when(stepRepository.findByPlanIdAndStatusOrderByStepOrder(planId, TaskStep.STATUS_PENDING))
                .thenReturn(List.of());
        when(agentService.chatTrustedSystemPrompt(
                org.mockito.ArgumentMatchers.contains("DELETE_CREATED_RESOURCE"),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.eq("CompensationService"),
                org.mockito.ArgumentMatchers.eq("json_rollback_prompt")))
                .thenReturn(new AiChatResponse(
                        "{\"status\":\"SUCCESS\",\"verified\":true,\"detail\":\"deleted\"}",
                        java.util.Map.of()));

        CompensationService service = new CompensationService(
                agentService,
                planRepository,
                stepRepository,
                sideEffectJournalRepository,
                new ObjectMapper(),
                metricsService);

        service.rollbackPlan(planId, "user-1");

        verify(sideEffectJournalRepository).save(argThat(saved ->
                SideEffectJournal.STATUS_VERIFIED.equals(saved.getStatus())
                        && saved.getExecutedAt() != null
                        && saved.getRollbackResult() != null
                        && saved.getRollbackResult().contains("\"verified\":true")));
        verify(stepRepository).save(argThat(saved ->
                TaskStep.STATUS_ROLLED_BACK.equals(saved.getStatus())));
        verify(planRepository).save(argThat(saved ->
                TaskPlan.STATUS_CANCELLED.equals(saved.getStatus())));
    }

    @Test
    void sourceDoesNotKeepDeadInferredCompensationCode() throws Exception {
        String source = Files.readString(SOURCE_FILE);

        assertThat(source)
                .doesNotContain("executeInferredCompensation(")
                .doesNotContain("inferReverseAction(");
    }

    @Test
    void verifyCompensation_requiresStructuredSuccessResponse() {
        CompensationService service = new CompensationService(
                agentService,
                planRepository,
                stepRepository,
                sideEffectJournalRepository,
                new ObjectMapper(),
                metricsService);

        Boolean naturalLanguageResult = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service,
                "verifyCompensation",
                "已删除成功",
                "create_note");
        Boolean structuredSuccess = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service,
                "verifyCompensation",
                "{\"status\":\"SUCCESS\",\"verified\":true,\"detail\":\"deleted created note\"}",
                "create_note");
        Boolean structuredFailure = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service,
                "verifyCompensation",
                "{\"status\":\"FAILED\",\"verified\":false,\"detail\":\"note still exists\"}",
                "create_note");

        assertThat(naturalLanguageResult).isFalse();
        assertThat(structuredSuccess).isTrue();
        assertThat(structuredFailure).isFalse();
    }

    @Test
    void sourceDoesNotUseKeywordBasedCompensationVerification() throws Exception {
        String source = Files.readString(SOURCE_FILE);

        assertThat(source)
                .doesNotContain("COMPENSATION_FAILURE_INDICATORS")
                .doesNotContain("lower.contains(\"成功\")")
                .doesNotContain("lower.contains(\"failed\")");
    }
}
