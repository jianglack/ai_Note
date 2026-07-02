package com.ainote.app.service;

import com.ainote.app.agent.AgentAssistant;
import com.ainote.app.agent.ConcurrencyGuard;
import com.ainote.app.agent.budget.TokenBudget;
import com.ainote.app.agent.guardrail.GuardrailResult;
import com.ainote.app.agent.guardrail.InputGuardrail;
import com.ainote.app.agent.guardrail.OutputGuardrail;
import com.ainote.app.agent.pending.PendingActionRegistry;
import com.ainote.app.agent.tools.ToolLoopDetector;
import com.ainote.app.memory.ReliableChatMemoryStore;
import com.ainote.app.model.AiChatResponse;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.UserMemoryRepository;
import com.ainote.app.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentServiceCapacityTest {

    private AgentAssistant agentAssistant;
    private ContextAssembler contextAssembler;
    private ConcurrencyGuard concurrencyGuard;
    private InputGuardrail inputGuardrail;
    private OutputGuardrail outputGuardrail;
    private ChatModel lightweightChatModel;
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        agentAssistant = mock(AgentAssistant.class);
        contextAssembler = mock(ContextAssembler.class);
        concurrencyGuard = mock(ConcurrencyGuard.class);
        inputGuardrail = mock(InputGuardrail.class);
        outputGuardrail = mock(OutputGuardrail.class);
        lightweightChatModel = mock(ChatModel.class);
        ObjectMapper objectMapper = new ObjectMapper();

        agentService = new AgentService(
                agentAssistant,
                mock(NoteRepository.class),
                mock(NoteService.class),
                mock(SecurityUtils.class),
                mock(UserMemoryRepository.class),
                objectMapper,
                new PendingActionRegistry(objectMapper),
                mock(ExecutorService.class),
                contextAssembler,
                mock(ToolCallAuditor.class),
                mock(ReliableChatMemoryStore.class),
                mock(MemoryExtractionService.class),
                mock(Tracer.class),
                mock(ToolLoopDetector.class),
                concurrencyGuard,
                mock(TokenBudget.class),
                inputGuardrail,
                outputGuardrail
        );
        agentService.setLightweightChatModel(lightweightChatModel);

        when(concurrencyGuard.tryAcquire(anyString(), anyLong())).thenReturn(true);
        when(inputGuardrail.check(anyString())).thenReturn(GuardrailResult.ok());
        when(outputGuardrail.sanitize(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void chatReturnsBusyWithoutAssemblingContextWhenGlobalCapacityIsFull() {
        AgentCapacityLimiter limiter = new AgentCapacityLimiter(1, 1, null);
        AgentCapacityLimiter.Permit held = limiter.tryAcquire("test");
        agentService.setAgentCapacityLimiter(limiter);

        try {
            AiChatResponse response = agentService.chat("hello", List.of(), "user-1");

            assertThat(response.getContent()).contains("AGENT_BUSY");
            verify(contextAssembler, never()).assemble(anyString(), anyList(), anyString());
            verify(agentAssistant, never()).chat(anyString(), anyString(), anyString(), anyString());
            verify(concurrencyGuard).release("user-1");
        } finally {
            held.close();
        }
    }

    @Test
    void chatStreamReturnsBusyWithoutAssemblingContextWhenGlobalCapacityIsFull() {
        AgentCapacityLimiter limiter = new AgentCapacityLimiter(1, 1, null);
        AgentCapacityLimiter.Permit held = limiter.tryAcquire("test");
        agentService.setAgentCapacityLimiter(limiter);
        CapturingStreamCallback callback = new CapturingStreamCallback();

        try {
            agentService.chatStream("hello", List.of(), "user-1", "req-1", callback);

            assertThat(callback.error).contains("AGENT_BUSY");
            assertThat(callback.complete).isNull();
            verify(contextAssembler, never()).assemble(anyString(), anyList(), anyString());
            verify(agentAssistant, never()).chat(anyString(), anyString(), anyString(), anyString());
            verify(concurrencyGuard).release("user-1");
        } finally {
            held.close();
        }
    }

    @Test
    void chatUsesLightweightAgentPathForSimpleChatWithoutContextAssembly() {
        when(contextAssembler.detectIntent("ok")).thenReturn(ContextAssembler.Intent.CHAT);
        when(lightweightChatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse("OK"));

        AiChatResponse response = agentService.chat("ok", List.of(), "user-1");

        assertThat(response.getContent()).isEqualTo("OK");
        assertThat(response.getChatMode()).isEqualTo("AGENT_LIGHTWEIGHT");
        verify(contextAssembler, never()).assemble(anyString(), anyList(), anyString());
        verify(agentAssistant, never()).chat(anyString(), anyString(), anyString(), anyString());
        verify(lightweightChatModel).chat(any(ChatRequest.class));
        verify(concurrencyGuard).release("user-1");
    }

    @Test
    void chatStreamUsesLightweightAgentPathForSimpleChatWithoutContextAssembly() {
        when(contextAssembler.detectIntent("ok")).thenReturn(ContextAssembler.Intent.CHAT);
        when(lightweightChatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse("OK"));
        CapturingStreamCallback callback = new CapturingStreamCallback();

        agentService.chatStream("ok", List.of(), "user-1", "req-1", callback);

        assertThat(callback.tokens.toString()).isEqualTo("OK");
        assertThat(callback.complete).isNotNull();
        assertThat(callback.complete.getChatMode()).isEqualTo("AGENT_LIGHTWEIGHT");
        assertThat(callback.error).isNull();
        verify(contextAssembler, never()).assemble(anyString(), anyList(), anyString());
        verify(agentAssistant, never()).chat(anyString(), anyString(), anyString(), anyString());
        verify(lightweightChatModel).chat(any(ChatRequest.class));
        verify(concurrencyGuard).release("user-1");
    }

    private static ChatResponse chatResponse(String text) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .build();
    }

    private static class CapturingStreamCallback implements AgentService.StreamCallback {
        private String error;
        private AiChatResponse complete;
        private final StringBuilder tokens = new StringBuilder();

        @Override
        public void onToken(String token) {
            tokens.append(token);
        }

        @Override
        public void onComplete(AiChatResponse response) {
            complete = response;
        }

        @Override
        public void onError(String error) {
            this.error = error;
        }

        @Override
        public void onProgress(String step, String detail) {
        }
    }
}
