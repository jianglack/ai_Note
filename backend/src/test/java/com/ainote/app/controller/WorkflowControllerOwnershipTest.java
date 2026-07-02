package com.ainote.app.controller;

import com.ainote.app.entity.AiWorkflow;
import com.ainote.app.model.WorkflowRequest;
import com.ainote.app.repository.AiWorkflowRepository;
import com.ainote.app.repository.AiWorkflowRunRepository;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.WorkflowExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
class WorkflowControllerOwnershipTest {
    private AiWorkflowRepository workflowRepository;    private AiWorkflowRunRepository runRepository;    private SecurityUtils securityUtils;    private WorkflowExecutionService workflowExecutionService;
    private WorkflowController controller;

    @BeforeEach
    void setUp() {
        workflowRepository = mock(AiWorkflowRepository.class);
        runRepository = mock(AiWorkflowRunRepository.class);
        securityUtils = mock(SecurityUtils.class);
        workflowExecutionService = mock(WorkflowExecutionService.class);
        controller = new WorkflowController(workflowRepository, runRepository, securityUtils, workflowExecutionService);
    }

    @Test
    void get_usesCurrentUserOwnershipLookup() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(workflowRepository.findByIdAndUserId("workflow-1", "user-1"))
                .thenReturn(Optional.of(new AiWorkflow()));

        controller.get("workflow-1");

        verify(workflowRepository).findByIdAndUserId("workflow-1", "user-1");
        verify(workflowRepository, never()).findById("workflow-1");
    }

    @Test
    void update_rejectsWorkflowNotOwnedByCurrentUser() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(workflowRepository.findByIdAndUserId("workflow-1", "user-1")).thenReturn(Optional.empty());

        WorkflowRequest request = new WorkflowRequest();
        request.setName("new");

        assertThatThrownBy(() -> controller.update("workflow-1", request))
                .isInstanceOf(NoSuchElementException.class);

        verify(workflowRepository, never()).save(any());
        verify(workflowRepository, never()).findById("workflow-1");
    }

    @Test
    void run_rejectsWorkflowNotOwnedByCurrentUserBeforeCreatingRun() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(workflowRepository.findByIdAndUserId("workflow-1", "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.run("workflow-1"))
                .isInstanceOf(NoSuchElementException.class);

        verify(runRepository, never()).save(any());
        verify(workflowExecutionService, never()).executeWorkflow(any(), any(), any());
    }

    @Test
    void getRuns_checksWorkflowOwnershipBeforeReturningRuns() {
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(workflowRepository.findByIdAndUserId("workflow-1", "user-1"))
                .thenReturn(Optional.of(new AiWorkflow()));

        controller.getRuns("workflow-1");

        verify(workflowRepository).findByIdAndUserId("workflow-1", "user-1");
        verify(runRepository).findByWorkflowIdOrderByStartedAtDesc("workflow-1");
    }
}
