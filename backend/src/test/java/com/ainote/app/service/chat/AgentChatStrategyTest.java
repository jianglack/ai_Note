package com.ainote.app.service.chat;

import com.ainote.app.model.AiChatResponse;
import com.ainote.app.service.AgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
