package com.ainote.app.service.chat;

import com.ainote.app.model.AiChatResponse;
import com.ainote.app.service.AgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentChatStrategyTest {

    private AgentService agentService;
    private AgentChatStrategy strategy;

    @BeforeEach
    void setUp() {
        agentService = Mockito.mock(AgentService.class);
        strategy = new AgentChatStrategy(agentService);
    }

    @Test
    void chatUsesChatAlreadyChecked() {
        AiChatResponse expected = new AiChatResponse("normal reply", new HashMap<>());
        when(agentService.chatAlreadyChecked("hello", List.of("1"), "user1")).thenReturn(expected);

        AiChatResponse result = strategy.chat("hello", List.of("1"), "user1");

        assertThat(result.getContent()).isEqualTo("normal reply");
        verify(agentService).chatAlreadyChecked("hello", List.of("1"), "user1");
        verify(agentService, never()).chat(anyString(), anyList(), anyString());
    }

    @Test
    void chatStreamUsesChatStreamAlreadyChecked() {
        StreamCallback callback = Mockito.mock(StreamCallback.class);

        strategy.chatStream("query", List.of(), "user-1", callback);

        verify(agentService).chatStreamAlreadyChecked(
                eq("query"), eq(List.of()), eq("user-1"),
                anyString(),
                any(AgentService.StreamCallback.class));
        verify(agentService, never()).chatStream(
                anyString(), anyList(), anyString(),
                any(AgentService.StreamCallback.class));
    }

    @Test
    void chatStreamPassesRequestIdWhenProvided() {
        StreamCallback callback = Mockito.mock(StreamCallback.class);

        strategy.chatStream("query", List.of(), "user-1", "req-1", callback);

        verify(agentService).chatStreamAlreadyChecked(
                eq("query"), eq(List.of()), eq("user-1"), eq("req-1"),
                any(AgentService.StreamCallback.class));
    }

    @Test
    void chatStreamCompletesRejectedConcurrencyError() {
        StreamCallback callback = Mockito.mock(StreamCallback.class);
        ArgumentCaptor<AgentService.StreamCallback> streamCallbackCaptor =
                ArgumentCaptor.forClass(AgentService.StreamCallback.class);

        strategy.chatStream("query", List.of(), "user-1", "req-1", callback);
        verify(agentService).chatStreamAlreadyChecked(
                eq("query"), eq(List.of()), eq("user-1"), eq("req-1"),
                streamCallbackCaptor.capture());

        streamCallbackCaptor.getValue().onError("您有一个正在进行的请求，请等待完成后再试。");

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(callback).onToken(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue()).contains("正在进行");

        ArgumentCaptor<AiChatResponse> responseCaptor = ArgumentCaptor.forClass(AiChatResponse.class);
        verify(callback).onComplete(responseCaptor.capture());
        assertThat(responseCaptor.getValue().getChatMode()).isEqualTo("REJECTED");
        assertThat(responseCaptor.getValue().getContent()).contains("等待完成");
    }

    @Test
    void chatStreamCompletesGlobalBusyErrorAsRejectedResponse() {
        StreamCallback callback = Mockito.mock(StreamCallback.class);
        ArgumentCaptor<AgentService.StreamCallback> streamCallbackCaptor =
                ArgumentCaptor.forClass(AgentService.StreamCallback.class);

        strategy.chatStream("query", List.of(), "user-1", "req-1", callback);
        verify(agentService).chatStreamAlreadyChecked(
                eq("query"), eq(List.of()), eq("user-1"), eq("req-1"),
                streamCallbackCaptor.capture());

        streamCallbackCaptor.getValue().onError("AGENT_BUSY: AI agent is busy. Please retry shortly.");

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(callback).onToken(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue()).contains("AGENT_BUSY");

        ArgumentCaptor<AiChatResponse> responseCaptor = ArgumentCaptor.forClass(AiChatResponse.class);
        verify(callback).onComplete(responseCaptor.capture());
        assertThat(responseCaptor.getValue().getChatMode()).isEqualTo("REJECTED");
        assertThat(responseCaptor.getValue().getContent()).contains("AGENT_BUSY");
    }

    @Test
    void chatThrowsWhenResponseIsNull() {
        when(agentService.chatAlreadyChecked("q", List.of(), "u")).thenReturn(null);

        assertThatThrownBy(() -> strategy.chat("q", List.of(), "u"))
                .isInstanceOf(ChatStrategyException.class)
                .hasFieldOrPropertyWithValue("errorCode", "AGENT_FAILED");
    }

    @Test
    void chatThrowsWhenResponseContentIsBlank() {
        AiChatResponse empty = new AiChatResponse("", new HashMap<>());
        when(agentService.chatAlreadyChecked("q", List.of(), "u")).thenReturn(empty);

        assertThatThrownBy(() -> strategy.chat("q", List.of(), "u"))
                .isInstanceOf(ChatStrategyException.class);
    }

    @Test
    void chatThrowsWhenResponseContainsFailureKeyword() {
        AiChatResponse timeout = new AiChatResponse("AGENT_TIMEOUT", new HashMap<>());
        when(agentService.chatAlreadyChecked("q", List.of(), "u")).thenReturn(timeout);

        assertThatThrownBy(() -> strategy.chat("q", List.of(), "u"))
                .isInstanceOf(ChatStrategyException.class)
                .extracting("originalContent")
                .isEqualTo("AGENT_TIMEOUT");
    }

    @Test
    void chatDoesNotThrowForRejection() {
        AiChatResponse rejection = new AiChatResponse("REQUEST_REJECTED_BY_GUARDRAIL", new HashMap<>());
        when(agentService.chatAlreadyChecked("q", List.of(), "u")).thenReturn(rejection);

        AiChatResponse result = strategy.chat("q", List.of(), "u");

        assertThat(result.getContent()).contains("REQUEST_REJECTED_BY_GUARDRAIL");
    }

    @Test
    void nameReturnsAgent() {
        assertThat(strategy.name()).isEqualTo("AGENT");
    }
}
