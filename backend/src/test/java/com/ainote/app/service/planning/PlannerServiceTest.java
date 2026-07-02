package com.ainote.app.service.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ainote.app.entity.TaskPlan;
import com.ainote.app.entity.TaskStep;
import com.ainote.app.repository.TaskPlanRepository;
import com.ainote.app.repository.TaskStepRepository;
import com.ainote.app.service.ContextAssembler;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PlannerServiceTest {

    private ChatModel chatModel;
    private ContextAssembler contextAssembler;
    private TaskPlanRepository planRepository;
    private TaskStepRepository stepRepository;
    private PlannerService service;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        contextAssembler = mock(ContextAssembler.class);
        planRepository = mock(TaskPlanRepository.class);
        stepRepository = mock(TaskStepRepository.class);
        service = new PlannerService(chatModel, contextAssembler, planRepository, stepRepository, new ObjectMapper());
        ReflectionTestUtils.setField(service, "maxSteps", 20);

        when(contextAssembler.assemble(any(String.class), any(), eq("user-1"))).thenReturn("note context");
        when(planRepository.save(any(TaskPlan.class))).thenAnswer(inv -> {
            TaskPlan plan = inv.getArgument(0);
            if (plan.getId() == null) {
                plan.setId("plan-1");
            }
            return plan;
        });
    }

    @Test
    void generatePlanPersistsPlanAndSteps() {
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse("""
                {
                  "goal": "整理项目",
                  "steps": [
                    {"order": 1, "action": "search", "description": "查资料", "params": {"query": "project"}},
                    {"order": 2, "action": "write", "description": "写摘要", "dependsOn": [1]}
                  ]
                }
                """));

        TaskPlan plan = service.generatePlan("整理项目", List.of("n1"), "user-1");

        assertThat(plan.getStatus()).isEqualTo(TaskPlan.STATUS_AWAITING_APPROVAL);
        assertThat(plan.getGoal()).contains("项目");
        assertThat(plan.getTotalSteps()).isEqualTo(2);
        ArgumentCaptor<TaskStep> stepCaptor = ArgumentCaptor.forClass(TaskStep.class);
        verify(stepRepository, org.mockito.Mockito.times(2)).save(stepCaptor.capture());
        assertThat(stepCaptor.getAllValues()).extracting(TaskStep::getAction)
                .containsExactly("search", "write");
        assertThat(stepCaptor.getAllValues().get(1).getDependsOn()).containsExactly(1);
    }

    @Test
    void generatePlanHandlesEmptyQueryWithFailedPlan() {
        TaskPlan plan = service.generatePlan("", List.of(), "user-1");

        assertThat(plan.getGoal()).isEmpty();
        assertThat(plan.getStatus()).isEqualTo(TaskPlan.STATUS_FAILED);
        assertThat(plan.getPlanJson()).contains("empty query");
    }

    @Test
    void generatePlanPersistsFailedPlanWhenLlmThrows() {
        when(chatModel.chat(any(ChatRequest.class))).thenThrow(new IllegalStateException("llm down"));

        TaskPlan plan = service.generatePlan("make a plan", List.of(), "user-1");

        assertThat(plan.getStatus()).isEqualTo(TaskPlan.STATUS_FAILED);
        assertThat(plan.getGoal()).isEqualTo("make a plan");
        assertThat(plan.getPlanJson()).contains("llm down");
    }

    private static ChatResponse chatResponse(String text) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text.trim()))
                .build();
    }
}
