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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock private UserMemoryRepository userMemoryRepository;
    @Mock private RagEvaluationService ragEvaluationService;
    @Mock private AgentEvaluationService agentEvaluationService;
    @Mock private AgentMetricsService agentMetricsService;
    @Mock private SecurityUtils securityUtils;
    @Mock private AdminAccessGuard adminAccessGuard;

    private AdminController controller;

    @BeforeEach
    void setUp() {
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
