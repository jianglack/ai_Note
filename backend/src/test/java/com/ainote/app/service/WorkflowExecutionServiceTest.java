package com.ainote.app.service;

import com.ainote.app.entity.AiWorkflow;
import com.ainote.app.entity.AiWorkflowRun;
import com.ainote.app.model.AiChatResponse;
import com.ainote.app.repository.AiWorkflowRepository;
import com.ainote.app.repository.AiWorkflowRunRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowExecutionServiceTest {

    private AgentService agentService;
    private AiWorkflowRepository workflowRepository;
    private AiWorkflowRunRepository runRepository;
    private ObjectMapper objectMapper;
    private WorkflowExecutionService service;

    @BeforeEach
    void setUp() {
        agentService = mock(AgentService.class);
        workflowRepository = mock(AiWorkflowRepository.class);
        runRepository = mock(AiWorkflowRunRepository.class);
        objectMapper = new ObjectMapper();
        service = new WorkflowExecutionService(agentService, workflowRepository, runRepository, objectMapper);
    }

    @Test
    void executeWorkflow_validSteps_marksRunCompletedAndPersistsResults() throws Exception {
        AiWorkflow workflow = workflow("workflow-1", """
                [
                  {"type":"ai_chat","instruction":"first step"},
                  {"type":"summarize","instruction":"second step"}
                ]
                """);
        AiWorkflowRun run = run("run-1", "workflow-1");
        when(runRepository.findById("run-1")).thenReturn(Optional.of(run));
        when(workflowRepository.findById("workflow-1")).thenReturn(Optional.of(workflow));
        when(agentService.chat(anyString(), eq(List.of()), eq("user-1")))
                .thenReturn(new AiChatResponse("first output", Map.of()))
                .thenReturn(new AiChatResponse("second output", Map.of()));

        service.executeWorkflow("workflow-1", "run-1", "user-1");

        assertThat(run.getStatus()).isEqualTo("completed");
        assertThat(run.getCompletedAt()).isNotNull();
        JsonNode results = objectMapper.readTree(run.getResults());
        assertThat(results).hasSize(2);
        assertThat(results.get(0).path("status").asText()).isEqualTo("completed");
        assertThat(results.get(0).path("output").asText()).isEqualTo("first output");
        assertThat(results.get(1).path("output").asText()).isEqualTo("second output");
        assertThat(workflow.getLastRunAt()).isNotNull();
        verify(runRepository, atLeast(2)).save(run);
        verify(workflowRepository).save(workflow);
    }

    @Test
    void executeWorkflow_missingRun_returnsWithoutSideEffects() {
        when(runRepository.findById("run-1")).thenReturn(Optional.empty());

        service.executeWorkflow("workflow-1", "run-1", "user-1");

        verify(runRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(workflowRepository, never()).findById("workflow-1");
        verify(agentService, never()).chat(anyString(), eq(List.of()), eq("user-1"));
    }

    @Test
    void executeWorkflow_missingWorkflow_marksRunFailed() {
        AiWorkflowRun run = run("run-1", "workflow-1");
        when(runRepository.findById("run-1")).thenReturn(Optional.of(run));
        when(workflowRepository.findById("workflow-1")).thenReturn(Optional.empty());

        service.executeWorkflow("workflow-1", "run-1", "user-1");

        assertThat(run.getStatus()).isEqualTo("failed");
        assertThat(run.getError()).isNotBlank();
        assertThat(run.getCompletedAt()).isNotNull();
        verify(runRepository, atLeast(2)).save(run);
        verify(agentService, never()).chat(anyString(), eq(List.of()), eq("user-1"));
    }

    @Test
    void executeWorkflow_stepFailure_recordsFailedStepButCompletesRun() throws Exception {
        AiWorkflow workflow = workflow("workflow-1", """
                [{"type":"ai_chat","instruction":"fail this step"}]
                """);
        AiWorkflowRun run = run("run-1", "workflow-1");
        when(runRepository.findById("run-1")).thenReturn(Optional.of(run));
        when(workflowRepository.findById("workflow-1")).thenReturn(Optional.of(workflow));
        when(agentService.chat(anyString(), eq(List.of()), eq("user-1")))
                .thenThrow(new RuntimeException("model unavailable"));

        service.executeWorkflow("workflow-1", "run-1", "user-1");

        assertThat(run.getStatus()).isEqualTo("completed");
        JsonNode results = objectMapper.readTree(run.getResults());
        assertThat(results.get(0).path("status").asText()).isEqualTo("failed");
        assertThat(results.get(0).path("error").asText()).contains("model unavailable");
    }

    private static AiWorkflow workflow(String id, String steps) {
        AiWorkflow workflow = new AiWorkflow();
        workflow.setId(id);
        workflow.setUserId("user-1");
        workflow.setName("workflow");
        workflow.setTriggerType("manual");
        workflow.setTriggerConfig("{}");
        workflow.setSteps(steps);
        return workflow;
    }

    private static AiWorkflowRun run(String id, String workflowId) {
        AiWorkflowRun run = new AiWorkflowRun();
        run.setId(id);
        run.setWorkflowId(workflowId);
        run.setStatus("queued");
        run.setStartedAt(LocalDateTime.now());
        return run;
    }
}
