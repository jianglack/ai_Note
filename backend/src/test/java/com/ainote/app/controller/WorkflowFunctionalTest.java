package com.ainote.app.controller;

import com.ainote.app.entity.AiWorkflow;
import com.ainote.app.entity.AiWorkflowRun;
import com.ainote.app.model.WorkflowRequest;
import com.ainote.app.repository.AiWorkflowRepository;
import com.ainote.app.repository.AiWorkflowRunRepository;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.WorkflowExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowFunctionalTest {

    private AiWorkflowRepository workflowRepository;
    private AiWorkflowRunRepository runRepository;
    private WorkflowExecutionService executionService;
    private WorkflowController controller;

    @BeforeEach
    void setUp() {
        workflowRepository = mock(AiWorkflowRepository.class);
        runRepository = mock(AiWorkflowRunRepository.class);
        SecurityUtils securityUtils = mock(SecurityUtils.class);
        executionService = mock(WorkflowExecutionService.class);
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        controller = new WorkflowController(workflowRepository, runRepository, securityUtils, executionService);
    }

    @Test
    void workflowCrudToggleRunAndRunHistory() {
        when(workflowRepository.save(any(AiWorkflow.class))).thenAnswer(invocation -> {
            AiWorkflow workflow = invocation.getArgument(0);
            if (workflow.getId() == null) {
                workflow.setId("workflow-1");
            }
            if (workflow.getEnabled() == null) {
                workflow.setEnabled(true);
            }
            return workflow;
        });

        WorkflowRequest create = new WorkflowRequest();
        AiWorkflow workflow = controller.create(create).getBody();

        assertThat(workflow.getName()).isEqualTo("New Workflow");
        assertThat(workflow.getTriggerType()).isEqualTo("manual");
        assertThat(workflow.getSteps()).isEqualTo("[]");

        when(workflowRepository.findByUserIdOrderByUpdatedAtDesc("user-1")).thenReturn(List.of(workflow));
        assertThat(controller.list().getBody()).containsExactly(workflow);

        when(workflowRepository.findByIdAndUserId("workflow-1", "user-1")).thenReturn(Optional.of(workflow));
        WorkflowRequest update = new WorkflowRequest();
        update.setName("Updated");
        update.setSteps("[{\"type\":\"ai_chat\"}]");
        AiWorkflow updated = controller.update("workflow-1", update).getBody();
        assertThat(updated.getName()).isEqualTo("Updated");
        assertThat(updated.getSteps()).contains("ai_chat");

        AiWorkflow toggled = controller.toggle("workflow-1").getBody();
        assertThat(toggled.getEnabled()).isFalse();

        when(runRepository.save(any(AiWorkflowRun.class))).thenAnswer(invocation -> {
            AiWorkflowRun run = invocation.getArgument(0);
            run.setId("run-1");
            return run;
        });
        Map<String, String> runBody = controller.run("workflow-1").getBody();
        assertThat(runBody).containsEntry("runId", "run-1").containsEntry("status", "queued");
        verify(executionService).executeWorkflow("workflow-1", "run-1", "user-1");

        AiWorkflowRun run = new AiWorkflowRun();
        run.setId("run-1");
        run.setWorkflowId("workflow-1");
        when(runRepository.findByWorkflowIdOrderByStartedAtDesc("workflow-1")).thenReturn(List.of(run));
        assertThat(controller.getRuns("workflow-1").getBody()).containsExactly(run);

        controller.delete("workflow-1");
        verify(workflowRepository).delete(workflow);
    }
}
