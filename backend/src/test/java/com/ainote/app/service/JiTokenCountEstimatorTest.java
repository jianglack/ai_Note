package com.ainote.app.service;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("JiTokenCountEstimator unit tests")
class JiTokenCountEstimatorTest {

    private JiTokenCountEstimator estimator;

    @BeforeEach
    void setUp() {
        estimator = new JiTokenCountEstimator(new JiTokenService());
    }

    @Test
    @DisplayName("estimateTokenCountInText returns same value as JiTokenService.countTokens")
    void estimateTokenCountInText_matchesJiTokenService() {
        JiTokenService service = new JiTokenService();
        String text = "Hello, this is a test message for token counting.";
        assertThat(estimator.estimateTokenCountInText(text))
                .isEqualTo(service.countTokens(text));
    }

    @Test
    @DisplayName("estimateTokenCountInText returns zero for empty string")
    void estimateTokenCountInText_emptyString_returnsZero() {
        assertThat(estimator.estimateTokenCountInText("")).isEqualTo(0);
    }

    @Test
    @DisplayName("UserMessage includes per-message overhead")
    void estimateTokenCountInMessage_userMessage_positive() {
        String text = "你好，请帮我总结一下这篇笔记";
        UserMessage msg = UserMessage.from(text);
        int count = estimator.estimateTokenCountInMessage(msg);
        int textOnly = estimator.estimateTokenCountInText(text);
        assertThat(count).isGreaterThan(textOnly);
        assertThat(count).isEqualTo(textOnly + 4);
    }

    @Test
    @DisplayName("UserMessage non-singleText does not throw")
    void estimateTokenCountInMessage_userMessage_nonSingleText_noException() {
        UserMessage msg = mock(UserMessage.class);
        when(msg.hasSingleText()).thenReturn(false);
        when(msg.contents()).thenReturn(List.of());
        int count = estimator.estimateTokenCountInMessage(msg);
        assertThat(count).isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("AiMessage text-only returns positive count")
    void estimateTokenCountInMessage_aiMessageTextOnly_positive() {
        AiMessage msg = AiMessage.from("这是 AI 的回复内容");
        int count = estimator.estimateTokenCountInMessage(msg);
        assertThat(count).isGreaterThan(0);
    }

    @Test
    @DisplayName("AiMessage with toolExecutionRequests estimates more than text-only AiMessage")
    void estimateTokenCountInMessage_aiMessageWithToolCalls_higherThanTextOnly() {
        AiMessage textOnly = AiMessage.from("执行操作");
        AiMessage withTools = AiMessage.from("执行操作", List.of(
                ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("searchNotes")
                        .arguments("{\"query\":\"test\"}")
                        .build()));
        int textOnlyCount = estimator.estimateTokenCountInMessage(textOnly);
        int withToolsCount = estimator.estimateTokenCountInMessage(withTools);
        assertThat(withToolsCount).isGreaterThan(textOnlyCount);
    }

    @Test
    @DisplayName("SystemMessage returns positive count")
    void estimateTokenCountInMessage_systemMessage_positive() {
        SystemMessage msg = SystemMessage.from("你是一个智能笔记助手");
        int count = estimator.estimateTokenCountInMessage(msg);
        assertThat(count).isGreaterThan(0);
    }

    @Test
    @DisplayName("ToolExecutionResultMessage includes toolName")
    void estimateTokenCountInMessage_toolResult_includesToolName() {
        ToolExecutionResultMessage msg = ToolExecutionResultMessage.from(
                "call-1", "searchNotes", "找到 3 条笔记");
        int count = estimator.estimateTokenCountInMessage(msg);
        int textOnly = estimator.estimateTokenCountInText("找到 3 条笔记");
        assertThat(count).isGreaterThan(textOnly);
    }

    @Test
    @DisplayName("estimateTokenCountInMessages is at least raw text token sum")
    void estimateTokenCountInMessages_greaterThanRawTextSum() {
        List<ChatMessage> messages = List.of(
                UserMessage.from("你好"),
                AiMessage.from("你好！有什么可以帮你的？"),
                UserMessage.from("帮我搜索笔记")
        );
        int totalByMessages = estimator.estimateTokenCountInMessages(messages);
        int rawTextSum = estimator.estimateTokenCountInText("你好")
                + estimator.estimateTokenCountInText("你好！有什么可以帮你的？")
                + estimator.estimateTokenCountInText("帮我搜索笔记");
        assertThat(totalByMessages).isGreaterThanOrEqualTo(rawTextSum);
    }
}
