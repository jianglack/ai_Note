package com.ainote.app.service.chat;

import com.ainote.app.agent.guardrail.GuardrailResult;
import com.ainote.app.agent.guardrail.InputGuardrail;
import com.ainote.app.model.AiChatResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatOrchestratorTest {

    private AgentChatStrategy agentStrategy;
    private ReadOnlyFallbackChatService fallbackStrategy;
    private ChatMetrics chatMetrics;
    private InputGuardrail inputGuardrail;
    private ChatOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        agentStrategy = Mockito.mock(AgentChatStrategy.class);
        fallbackStrategy = Mockito.mock(ReadOnlyFallbackChatService.class);
        chatMetrics = Mockito.mock(ChatMetrics.class);
        inputGuardrail = Mockito.mock(InputGuardrail.class);

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(10)
                        .build());

        when(inputGuardrail.check(anyString())).thenReturn(GuardrailResult.ok());

        orchestrator = new ChatOrchestrator(
                agentStrategy, fallbackStrategy, registry, chatMetrics, inputGuardrail);
    }

    @Test
    void agentSuccessReturnsModeAgent() {
        AiChatResponse agentResp = new AiChatResponse("agent reply", new HashMap<>());
        when(agentStrategy.chat("hi", List.of(), "u1")).thenReturn(agentResp);

        AiChatResponse result = orchestrator.chat("hi", List.of(), "u1");

        assertThat(result.getChatMode()).isEqualTo("AGENT");
        assertThat(result.isDegraded()).isFalse();
        verify(chatMetrics).recordSuccess(eq("AGENT"), anyLong());
    }

    @Test
    void agentFailureFallsBackToFallbackStrategy() {
        when(agentStrategy.chat(anyString(), anyList(), anyString()))
                .thenThrow(new ChatStrategyException("AGENT_FAILED", "timeout", null));
        AiChatResponse fallbackResp = new AiChatResponse("fallback reply", new HashMap<>());
        fallbackResp.setDegraded(true);
        fallbackResp.setChatMode("FALLBACK");
        when(fallbackStrategy.chat(anyString(), anyList(), anyString())).thenReturn(fallbackResp);

        AiChatResponse result = orchestrator.chat("q", List.of(), "u1");

        assertThat(result.isDegraded()).isTrue();
        assertThat(result.getChatMode()).isEqualTo("FALLBACK");
        verify(chatMetrics).recordFallback(eq("AGENT_ERROR_RESPONSE"), anyLong());
    }

    @Test
    void inputGuardrailBlockedReturnsRejectedWithoutFallback() {
        when(inputGuardrail.check("malicious")).thenReturn(GuardrailResult.blocked("unsafe"));

        AiChatResponse result = orchestrator.chat("malicious", List.of(), "u1");

        assertThat(result.getChatMode()).isEqualTo("REJECTED");
        assertThat(result.isDegraded()).isFalse();
        assertThat(result.getContent()).isEqualTo("unsafe");
        verify(agentStrategy, never()).chat(anyString(), anyList(), anyString());
        verify(fallbackStrategy, never()).chat(anyString(), anyList(), anyString());
    }

    @Test
    void bothStrategiesFailReturnsError() {
        when(agentStrategy.chat(anyString(), anyList(), anyString()))
                .thenThrow(new RuntimeException("agent down"));
        when(fallbackStrategy.chat(anyString(), anyList(), anyString()))
                .thenThrow(new RuntimeException("fallback down"));

        AiChatResponse result = orchestrator.chat("q", List.of(), "u1");

        assertThat(result.getChatMode()).isEqualTo("ERROR");
        assertThat(result.isDegraded()).isTrue();
        verify(chatMetrics).recordTotalFailure();
    }
}
