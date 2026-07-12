package com.ainote.app.service.chat;

import com.ainote.app.agent.guardrail.GuardrailResult;
import com.ainote.app.agent.guardrail.InputGuardrail;
import com.ainote.app.model.AiChatResponse;
import com.ainote.app.model.memory.MemoryForgetResponse;
import com.ainote.app.service.MemoryControlService;
import com.ainote.app.service.MemoryPrivacyService;
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
    private ReadOnlyStreamingChatService readOnlyStreamingChatService;
    private ChatMetrics chatMetrics;
    private InputGuardrail inputGuardrail;
    private MemoryControlService memoryControlService;
    private ChatOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        agentStrategy = Mockito.mock(AgentChatStrategy.class);
        fallbackStrategy = Mockito.mock(ReadOnlyFallbackChatService.class);
        readOnlyStreamingChatService = Mockito.mock(ReadOnlyStreamingChatService.class);
        chatMetrics = Mockito.mock(ChatMetrics.class);
        inputGuardrail = Mockito.mock(InputGuardrail.class);
        memoryControlService = Mockito.mock(MemoryControlService.class);

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(10)
                        .build());

        when(inputGuardrail.check(anyString())).thenReturn(GuardrailResult.ok());

        orchestrator = new ChatOrchestrator(
                agentStrategy,
                fallbackStrategy,
                readOnlyStreamingChatService,
                registry,
                chatMetrics,
                inputGuardrail,
                new MemoryPrivacyService(),
                memoryControlService);
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
    void sensitiveMemoryWriteRequestIsRejectedBeforeModelCall() {
        AiChatResponse result = orchestrator.chat(
                "请记住我的身份证号：110105199003077614，手机号：13812345678。",
                List.of(),
                "u1");

        assertThat(result.getChatMode()).isEqualTo("MEMORY_GOVERNED");
        assertThat(result.getContent()).contains("不能为你保存");
        verify(agentStrategy, never()).chat(anyString(), anyList(), anyString());
        verify(fallbackStrategy, never()).chat(anyString(), anyList(), anyString());
    }

    @Test
    void missingLongTermMemoryReturnsGovernedHonestResponseBeforeModelCall() {
        when(memoryControlService.hasActiveMemories("u1")).thenReturn(false);

        AiChatResponse result = orchestrator.chat(
                "根据你记住的我的偏好，给我一个建议。",
                List.of(),
                "u1");

        assertThat(result.getChatMode()).isEqualTo("MEMORY_GOVERNED");
        assertThat(result.getContent()).contains("没有与你这个问题对应的可用长期记忆");
        assertThat(result.getContent()).contains("不能假装知道");
        verify(agentStrategy, never()).chat(anyString(), anyList(), anyString());
        verify(fallbackStrategy, never()).chat(anyString(), anyList(), anyString());
    }

    @Test
    void streamingMissingLongTermMemoryDoesNotEnterAnyModel() {
        when(memoryControlService.hasActiveMemories("u1")).thenReturn(false);
        CapturingCallback callback = new CapturingCallback();

        orchestrator.chatStream(
                "What do you remember about me and my saved preference?",
                List.of(),
                "u1",
                callback);

        assertThat(callback.complete.getChatMode()).isEqualTo("MEMORY_GOVERNED");
        assertThat(callback.complete.getContent()).contains("没有与你这个问题对应的可用长期记忆");
        verify(readOnlyStreamingChatService, never()).chatStream(anyString(), anyList(), anyString(), Mockito.any());
        verify(agentStrategy, never()).chatStream(anyString(), anyList(), anyString(), anyString(), Mockito.any());
    }

    @Test
    void naturalLanguageForgetAllRequestCallsMemoryControlService() {
        when(memoryControlService.forgetAllActiveMemories(eq("u1"), anyString()))
                .thenReturn(new MemoryForgetResponse(4));

        AiChatResponse result = orchestrator.chat("这些记忆都不对，请全部删除。", List.of(), "u1");

        assertThat(result.getChatMode()).isEqualTo("MEMORY_GOVERNED");
        assertThat(result.getContent()).contains("已删除你的长期记忆 4 条");
        verify(memoryControlService).forgetAllActiveMemories(eq("u1"), anyString());
        verify(memoryControlService, never()).forgetMemories(eq("u1"), Mockito.any());
        verify(agentStrategy, never()).chat(anyString(), anyList(), anyString());
    }

    @Test
    void streamingNaturalLanguageForgetAllRequestDoesNotEnterModel() {
        when(memoryControlService.forgetAllActiveMemories(eq("u1"), anyString()))
                .thenReturn(new MemoryForgetResponse(4));
        CapturingCallback callback = new CapturingCallback();

        orchestrator.chatStream("这些记忆都不对，请全部删除。", List.of(), "u1", callback);

        assertThat(callback.complete.getChatMode()).isEqualTo("MEMORY_GOVERNED");
        assertThat(callback.tokens.toString()).contains("已删除你的长期记忆 4 条");
        verify(memoryControlService).forgetAllActiveMemories(eq("u1"), anyString());
        verify(agentStrategy, never()).chatStream(anyString(), anyList(), anyString(), anyString(), Mockito.any());
        verify(readOnlyStreamingChatService, never()).chatStream(anyString(), anyList(), anyString(), Mockito.any());
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

    @Test
    void streamUsesDirectReadOnlyStreamingWhenEligible() {
        when(readOnlyStreamingChatService.canHandle("summarize", List.of()))
                .thenReturn(true);
        when(readOnlyStreamingChatService.chatStream(eq("summarize"), eq(List.of()), eq("u1"), Mockito.any()))
                .thenAnswer(invocation -> {
                    StreamCallback callback = invocation.getArgument(3);
                    callback.onToken("ok");
                    callback.onComplete(new AiChatResponse("ok", new HashMap<>()));
                    return true;
                });
        CapturingCallback callback = new CapturingCallback();

        orchestrator.chatStream("summarize", List.of(), "u1", callback);

        assertThat(callback.tokens).contains("ok");
        assertThat(callback.complete.getChatMode()).isNull();
        verify(agentStrategy, never()).chatStream(anyString(), anyList(), anyString(), anyString(), Mockito.any());
        verify(chatMetrics).recordSuccess(eq("DIRECT_STREAM"), anyLong());
    }

    @Test
    void streamKeepsDestructiveRequestsOnAgentStrategy() {
        when(readOnlyStreamingChatService.canHandle("delete current note", List.of("n1")))
                .thenReturn(false);
        CapturingCallback callback = new CapturingCallback();

        orchestrator.chatStream("delete current note", List.of("n1"), "u1", callback);

        verify(agentStrategy).chatStream(eq("delete current note"), eq(List.of("n1")), eq("u1"), anyString(), Mockito.any());
        verify(readOnlyStreamingChatService, never())
                .chatStream(anyString(), anyList(), anyString(), Mockito.any());
    }

    private static class CapturingCallback implements StreamCallback {
        private final StringBuilder tokens = new StringBuilder();
        private AiChatResponse complete;

        @Override
        public void onToken(String token) {
            tokens.append(token);
        }

        @Override
        public void onComplete(AiChatResponse response) {
            complete = response;
        }

        @Override
        public void onError(String errorMessage) {
        }

        @Override
        public void onProgress(String step, String detail) {
        }
    }
}
