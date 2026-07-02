package com.ainote.app.controller;

import com.ainote.app.model.AdminAgentEvaluationRequest;
import com.ainote.app.model.AdminRagEvaluationRequest;
import com.ainote.app.repository.UserMemoryRepository;
import com.ainote.app.security.AdminAccessGuard;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.AgentEvaluationService;
import com.ainote.app.service.AgentMetricsService;
import com.ainote.app.service.RagEvaluationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
class AdminControllerTest {
    private UserMemoryRepository userMemoryRepository;
    private RagEvaluationService ragEvaluationService;
    private AgentEvaluationService agentEvaluationService;
    private AgentMetricsService agentMetricsService;
    private SecurityUtils securityUtils;
    private AdminAccessGuard adminAccessGuard;
    private AdminController controller;

    @BeforeEach
    void setUp() {
        userMemoryRepository = mock(UserMemoryRepository.class);
        ragEvaluationService = mock(RagEvaluationService.class);
        agentEvaluationService = mock(AgentEvaluationService.class);
        agentMetricsService = mock(AgentMetricsService.class);
        securityUtils = mock(SecurityUtils.class);
        adminAccessGuard = mock(AdminAccessGuard.class);
        controller = new AdminController(
                userMemoryRepository,
                ragEvaluationService,
                agentEvaluationService,
                agentMetricsService,
                securityUtils,
                adminAccessGuard);
    }

    @Test
    void evaluateRag_doesNotSwallowInternalExceptionsAsBadRequest() {
        AdminRagEvaluationRequest body = new AdminRagEvaluationRequest();
        AdminRagEvaluationRequest.EvalCaseRequest evalCase = new AdminRagEvaluationRequest.EvalCaseRequest();
        evalCase.setQuery("query");
        evalCase.setExpectedNoteIds(List.of("note-1"));
        body.setCases(List.of(evalCase));

        when(securityUtils.getCurrentUserId()).thenReturn("admin-user");
        when(ragEvaluationService.evaluate(any(), eq(5), eq(0.5), eq("admin-user")))
                .thenThrow(new IllegalStateException("rag evaluator unavailable"));

        assertThatThrownBy(() -> controller.evaluateRag(body))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rag evaluator unavailable");
    }

    @Test
    void evaluateAgent_doesNotSwallowInternalExceptionsAsBadRequest() {
        AdminAgentEvaluationRequest body = new AdminAgentEvaluationRequest();
        AdminAgentEvaluationRequest.AgentEvalCaseRequest evalCase =
                new AdminAgentEvaluationRequest.AgentEvalCaseRequest();
        evalCase.setUserQuery("create a note");
        body.setCases(List.of(evalCase));

        when(securityUtils.getCurrentUserId()).thenReturn("admin-user");
        when(agentEvaluationService.evaluate(any(), eq("admin-user")))
                .thenThrow(new IllegalStateException("agent evaluator unavailable"));

        assertThatThrownBy(() -> controller.evaluateAgent(body))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agent evaluator unavailable");
    }
}
