package com.ainote.app.service;

import com.ainote.app.agent.AgentAssistant;
import com.ainote.app.agent.ConcurrencyGuard;
import com.ainote.app.agent.budget.TokenBudget;
import com.ainote.app.agent.guardrail.GuardrailResult;
import com.ainote.app.agent.guardrail.InputGuardrail;
import com.ainote.app.agent.guardrail.OutputGuardrail;
import com.ainote.app.agent.pending.PendingActionRegistry;
import com.ainote.app.memory.DeferredMemoryState;
import com.ainote.app.memory.ReliableChatMemoryStore;
import com.ainote.app.model.AiChatResponse;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.UserMemoryRepository;
import com.ainote.app.security.SecurityUtils;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@DisplayName("AgentService memory deferred flush tests")
class AgentServiceMemoryFlushTest {
    private AgentAssistant agentAssistant;
    private NoteRepository noteRepository;
    private NoteService noteService;
    private SecurityUtils securityUtils;
    private UserMemoryRepository userMemoryRepository;
    private ContextAssembler contextAssembler;
    private ToolCallAuditor toolCallAuditor;
    private ReliableChatMemoryStore reliableChatMemoryStore;
    private MemoryExtractionService memoryExtractionService;
    private Tracer tracer;
    private com.ainote.app.agent.tools.ToolLoopDetector toolLoopDetector;
    private ConcurrencyGuard concurrencyGuard;
    private TokenBudget tokenBudget;
    private InputGuardrail inputGuardrail;
    private OutputGuardrail outputGuardrail;
    private AgentService agentService;
    private ExecutorService realExecutor;

    @BeforeEach
    void setUp() {
        agentAssistant = mock(AgentAssistant.class);
        noteRepository = mock(NoteRepository.class);
        noteService = mock(NoteService.class);
        securityUtils = mock(SecurityUtils.class);
        userMemoryRepository = mock(UserMemoryRepository.class);
        contextAssembler = mock(ContextAssembler.class);
        toolCallAuditor = mock(ToolCallAuditor.class);
        reliableChatMemoryStore = mock(ReliableChatMemoryStore.class);
        memoryExtractionService = mock(MemoryExtractionService.class);
        tracer = mock(Tracer.class);
        toolLoopDetector = mock(com.ainote.app.agent.tools.ToolLoopDetector.class);
        concurrencyGuard = mock(ConcurrencyGuard.class);
        tokenBudget = mock(TokenBudget.class);
        inputGuardrail = mock(InputGuardrail.class);
        outputGuardrail = mock(OutputGuardrail.class);
        realExecutor = Executors.newSingleThreadExecutor();
        ObjectMapper objectMapper = new ObjectMapper();
        agentService = new AgentService(
                agentAssistant, noteRepository, noteService, securityUtils,
                userMemoryRepository, objectMapper, new PendingActionRegistry(objectMapper),
                realExecutor, contextAssembler,
                toolCallAuditor, reliableChatMemoryStore, memoryExtractionService,
                tracer, toolLoopDetector, concurrencyGuard, tokenBudget,
                inputGuardrail, outputGuardrail);
        ReflectionTestUtils.setField(agentService, "agentTimeoutSeconds", 30);

        lenient().when(concurrencyGuard.tryAcquire(anyString(), anyLong())).thenReturn(true);

        SpanBuilder spanBuilder = mock(SpanBuilder.class);
        Span span = mock(Span.class);
        lenient().when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        lenient().when(spanBuilder.setAttribute(anyString(), anyString())).thenReturn(spanBuilder);
        lenient().when(spanBuilder.setAttribute(anyString(), anyLong())).thenReturn(spanBuilder);
        lenient().when(spanBuilder.startSpan()).thenReturn(span);

        lenient().when(reliableChatMemoryStore.getMessages(anyString())).thenReturn(List.of());
        lenient().when(inputGuardrail.scanUntrustedContent(anyString())).thenReturn(GuardrailResult.ok());
    }

    @AfterEach
    void tearDown() {
        realExecutor.shutdownNow();
        DeferredMemoryState.clear();
    }

    @Test
    void successfulFlushNotDegraded() {
        stubSuccessfulAgentResponse("normal reply");

        AiChatResponse response = agentService.chat("hello", List.of(), "user-1");

        assertThat(response.getContent()).isEqualTo("normal reply");
        assertThat(response.isDegraded()).isFalse();
        verify(reliableChatMemoryStore).flushDeferredWrites();
    }

    @Test
    void flushFailureDegraded() {
        stubSuccessfulAgentResponse("normal reply");
        doThrow(new RuntimeException("DB flush failed"))
                .when(reliableChatMemoryStore).flushDeferredWrites();

        AiChatResponse response = agentService.chat("hello", List.of(), "user-1");

        assertThat(response.getContent()).isEqualTo("normal reply");
        assertThat(response.isDegraded()).isTrue();
        assertThat(response.getDegradationReason()).contains("memory");
    }

    @Test
    void deferredStateClearedRegardless() throws Exception {
        stubSuccessfulAgentResponse("reply");

        doAnswer(invocation -> {
            assertThat(DeferredMemoryState.isActive())
                    .as("DeferredMemoryState should be active when flush is called")
                    .isTrue();
            return null;
        }).when(reliableChatMemoryStore).flushDeferredWrites();

        agentService.chat("hello", List.of(), "user-1");

        verify(reliableChatMemoryStore).flushDeferredWrites();
        boolean isActiveOnExecutorThread = realExecutor.submit(
                DeferredMemoryState::isActive
        ).get();
        assertThat(isActiveOnExecutorThread)
                .as("DeferredMemoryState must be cleared on executor thread after chat()")
                .isFalse();
    }

    @Test
    void agentExceptionNoFlushPendingDiscarded() throws Exception {
        when(inputGuardrail.check(anyString())).thenReturn(GuardrailResult.ok());
        when(contextAssembler.assemble(anyString(), anyList(), anyString())).thenReturn("context");
        when(agentAssistant.chat(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("LLM provider down"));

        AiChatResponse response = agentService.chat("hello", List.of(), "user-1");

        assertThat(response.getContent()).contains("LLM provider down");
        verify(reliableChatMemoryStore, never()).flushDeferredWrites();
        boolean isActiveOnExecutorThread = realExecutor.submit(
                DeferredMemoryState::isActive
        ).get();
        assertThat(isActiveOnExecutorThread)
                .as("DeferredMemoryState must be cleared even when Agent throws")
                .isFalse();
    }

    private void stubSuccessfulAgentResponse(String content) {
        when(inputGuardrail.check(anyString())).thenReturn(GuardrailResult.ok());
        when(contextAssembler.assemble(anyString(), anyList(), anyString())).thenReturn("context");
        when(agentAssistant.chat(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(content);
        when(toolCallAuditor.validate(anyString(), anyString(), any(), anyInt()))
                .thenReturn(content);
        when(outputGuardrail.sanitize(anyString(), anyString())).thenReturn(content);
    }
}
