package com.ainote.app.service.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmTaskRouterServiceTest {

    @Test
    void taskRouterPromptResourceIsLoadableFromClasspath() throws Exception {
        ClassPathResource resource = new ClassPathResource("prompts/task-router-system.txt");

        assertThat(resource.exists()).isTrue();
        String prompt = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(prompt)
                .contains("DIRECT_AGENT")
                .contains("PLANNED_TASK")
                .contains("JSON");
    }

    @Test
    void routesPlannedTaskWhenModelReturnsPlannedTask() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response("""
                {"route":"PLANNED_TASK","confidence":0.98,"reason":"批量检索后还要打标签和创建日程","requiresUserPlanApproval":true,"estimatedToolSteps":5,"riskLevel":"MEDIUM"}
                """));

        LlmTaskRouterService router = new LlmTaskRouterService(chatModel, new ObjectMapper());

        TaskRouteDecision decision = router.route(
                "请先查找所有 RAG 相关笔记，然后总结，再统一加标签，最后创建复习日程。",
                List.of()
        );

        assertThat(decision.route()).isEqualTo(TaskRoute.PLANNED_TASK);
        assertThat(decision.requiresUserPlanApproval()).isTrue();
        assertThat(decision.estimatedToolSteps()).isEqualTo(5);
    }

    @Test
    void routesDirectAgentWhenModelReturnsDirectAgent() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response("""
                {"route":"DIRECT_AGENT","confidence":0.91,"reason":"只是检索并总结，可由普通 Agent 一次完成","requiresUserPlanApproval":false,"estimatedToolSteps":2,"riskLevel":"LOW"}
                """));

        LlmTaskRouterService router = new LlmTaskRouterService(chatModel, new ObjectMapper());

        TaskRouteDecision decision = router.route("查找 RAG 相关笔记并总结一下。", List.of());

        assertThat(decision.route()).isEqualTo(TaskRoute.DIRECT_AGENT);
        assertThat(decision.requiresUserPlanApproval()).isFalse();
    }

    @Test
    void fallsBackToDirectAgentWhenModelReturnsInvalidJson() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response("我认为应该直接执行。"));

        LlmTaskRouterService router = new LlmTaskRouterService(chatModel, new ObjectMapper());

        TaskRouteDecision decision = router.route("删除当前笔记", List.of("note-1"));

        assertThat(decision.route()).isEqualTo(TaskRoute.DIRECT_AGENT);
        assertThat(decision.confidence()).isEqualTo(0.0);
        assertThat(decision.reason()).contains("路由模型输出无法解析");
    }

    private static ChatResponse response(String text) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text.trim()))
                .build();
    }
}
