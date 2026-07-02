package com.ainote.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ainote.app.model.AiChatResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentEvaluationServiceTest {

    private AgentService agentService;
    private AgentEvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        agentService = mock(AgentService.class);
        evaluationService = new AgentEvaluationService(agentService);
    }

    @Test
    void evaluatesToolAccuracyAndTaskCompletion() {
        AgentEvaluationService.AgentEvalCase createCase = new AgentEvaluationService.AgentEvalCase();
        createCase.userQuery = "创建规划笔记";
        createCase.expectedAction = "create";
        createCase.expectSuccess = true;
        createCase.expectedResponseKeywords = List.of("规划");

        AgentEvaluationService.AgentEvalCase searchCase = new AgentEvaluationService.AgentEvalCase();
        searchCase.userQuery = "查找会议";
        searchCase.expectedAction = "search";
        searchCase.expectSuccess = true;
        searchCase.expectedResponseKeywords = List.of("不存在");

        when(agentService.chat("创建规划笔记", List.of(), "user-1"))
                .thenReturn(new AiChatResponse("已创建笔记：规划", Map.of()));
        when(agentService.chat("查找会议", List.of(), "user-1"))
                .thenReturn(new AiChatResponse("找到 3 条笔记", Map.of()));

        AgentEvaluationService.AgentEvalReport report =
                evaluationService.evaluate(List.of(createCase, searchCase), "user-1");

        assertThat(report.totalCases).isEqualTo(2);
        assertThat(report.toolCorrectCount).isEqualTo(2);
        assertThat(report.taskCompleteCount).isEqualTo(1);
        assertThat(report.toolAccuracy).isEqualTo(1.0);
        assertThat(report.taskCompletionRate).isEqualTo(0.5);
        assertThat(report.details.get(0).matchedKeywords).containsExactly("规划");
        assertThat(report.details.get(1).missedKeywords).containsExactly("不存在");
    }

    @Test
    void recordsEvaluationErrorsAsFailedResults() {
        AgentEvaluationService.AgentEvalCase evalCase = new AgentEvaluationService.AgentEvalCase();
        evalCase.userQuery = "删除笔记";
        evalCase.expectedAction = "delete";
        evalCase.expectSuccess = true;
        evalCase.expectedResponseKeywords = List.of("删除");

        when(agentService.chat("删除笔记", List.of(), "user-1"))
                .thenThrow(new RuntimeException("agent unavailable"));

        AgentEvaluationService.AgentEvalReport report =
                evaluationService.evaluate(List.of(evalCase), "user-1");

        assertThat(report.totalCases).isEqualTo(1);
        assertThat(report.toolCorrectCount).isZero();
        assertThat(report.taskCompleteCount).isZero();
        assertThat(report.details.get(0).error).contains("agent unavailable");
    }
}
