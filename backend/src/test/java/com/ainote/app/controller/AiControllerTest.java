package com.ainote.app.controller;

import com.ainote.app.agent.CancellationToken;
import com.ainote.app.entity.AgentTrace;
import com.ainote.app.model.AiChatRequest;
import com.ainote.app.model.AiChatResponse;
import com.ainote.app.repository.AgentTraceRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.AgentService;
import com.ainote.app.service.AiService;
import com.ainote.app.service.LangChain4jRagService;
import com.ainote.app.service.RagFeedbackService;
import com.ainote.app.service.SmartSuggestionService;
import com.ainote.app.service.chat.ChatMetrics;
import com.ainote.app.service.chat.ChatOrchestrator;
import com.ainote.app.service.chat.StreamCallback;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
@DisplayName("AiController 单元测试")
class AiControllerTest {
    private AiService aiService;
    private SecurityUtils securityUtils;
    private AgentTraceRepository traceRepository;
    private ExecutorService executorService;
    private RagFeedbackService ragFeedbackService;
    private SmartSuggestionService smartSuggestionService;
    private NoteRepository noteRepository;
    private AgentService agentService;
    private LangChain4jRagService ragService;
    private ChatOrchestrator chatOrchestrator;
    @BeforeEach
    void setUpMocks() {
        aiService = mock(AiService.class);
        securityUtils = mock(SecurityUtils.class);
        traceRepository = mock(AgentTraceRepository.class);
        executorService = mock(ExecutorService.class);
        ragFeedbackService = mock(RagFeedbackService.class);
        smartSuggestionService = mock(SmartSuggestionService.class);
        noteRepository = mock(NoteRepository.class);
        agentService = mock(AgentService.class);
        ragService = mock(LangChain4jRagService.class);
        chatOrchestrator = mock(ChatOrchestrator.class);
    }

    @Test
    @DisplayName("getTraces 应支持时间范围和模型筛选")
    void shouldFilterTracesByDateRangeAndModel() {
        AiController controller = new AiController(
            aiService, securityUtils, traceRepository, new ObjectMapper(), executorService,
            ragFeedbackService, smartSuggestionService, noteRepository, agentService, ragService,
            chatOrchestrator
        );
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 4, 23, 59);
        AgentTrace trace = new AgentTrace();
        trace.setTraceId("trace-1");

        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(traceRepository.findFilteredTraces(
                org.mockito.Mockito.eq("user-123"),
                org.mockito.Mockito.eq(start),
                org.mockito.Mockito.eq(end),
                org.mockito.Mockito.eq("deepseek-chat"),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
            .thenReturn(List.of(trace));

        var response = controller.getTraces(25, start, end, "deepseek-chat");

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).traceId()).isEqualTo(trace.getTraceId());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(traceRepository).findFilteredTraces(
            org.mockito.Mockito.eq("user-123"),
            org.mockito.Mockito.eq(start),
            org.mockito.Mockito.eq(end),
            org.mockito.Mockito.eq("deepseek-chat"),
            pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(25);
    }

    @Test
    void getTraces_returnsDtoWithoutUserId() throws Exception {
        AiController controller = new AiController(
            aiService, securityUtils, traceRepository, new ObjectMapper(), executorService,
            ragFeedbackService, smartSuggestionService, noteRepository, agentService, ragService,
            chatOrchestrator
        );
        AgentTrace trace = new AgentTrace();
        trace.setId("trace-row-1");
        trace.setUserId("user-secret");
        trace.setTraceId("trace-1");
        trace.setInputText("input");
        trace.setOutputText("output");
        trace.setTotalTokens(12);

        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(traceRepository.findFilteredTraces(
                org.mockito.Mockito.eq("user-123"),
                org.mockito.Mockito.isNull(),
                org.mockito.Mockito.isNull(),
                org.mockito.Mockito.isNull(),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
            .thenReturn(List.of(trace));

        var response = controller.getTraces(25, null, null, null);

        String json = new ObjectMapper().writeValueAsString(response.getBody());
        assertThat(json).contains("\"id\":\"trace-row-1\"");
        assertThat(json).contains("\"traceId\":\"trace-1\"");
        assertThat(json).doesNotContain("userId");
        assertThat(json).doesNotContain("user-secret");
    }

    @Test
    void getTraces_doesNotSwallowInternalExceptionsAsBadRequest() {
        AiController controller = new AiController(
            aiService, securityUtils, traceRepository, new ObjectMapper(), executorService,
            ragFeedbackService, smartSuggestionService, noteRepository, agentService, ragService,
            chatOrchestrator
        );

        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(traceRepository.findFilteredTraces(
                org.mockito.Mockito.eq("user-123"),
                org.mockito.Mockito.isNull(),
                org.mockito.Mockito.isNull(),
                org.mockito.Mockito.isNull(),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
            .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> controller.getTraces(25, null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database unavailable");
    }

    @Test
    @DisplayName("generateCanvas \u5f02\u5e38\u65f6\u4e0d\u6cc4\u9732\u5185\u90e8\u9519\u8bef\u4fe1\u606f")
    void generateCanvas_shouldNotLeakExceptionMessage() {
        AiController controller = new AiController(
            aiService, securityUtils, traceRepository, new ObjectMapper(), executorService,
            ragFeedbackService, smartSuggestionService, noteRepository, agentService, ragService,
            chatOrchestrator
        );

        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(noteRepository.findByUserIdAndDeletedAtIsNull("user-123"))
                .thenThrow(new RuntimeException("secret internal path /tmp/foo"));

        var response = controller.generateCanvas(null);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        Map<String, Object> body = response.getBody();
        assertThat(body.get("error").toString()).contains("\u5d4c\u5165\u53ef\u89c6\u5316");
        assertThat(body.get("error").toString()).doesNotContain("secret internal path");
    }

    @Test
    void chatStream_returns503WhenSecurityExecutorRejectsInitialWork() {
        AiController controller = new AiController(
            aiService, securityUtils, traceRepository, new ObjectMapper(), executorService,
            ragFeedbackService, smartSuggestionService, noteRepository, agentService, ragService,
            chatOrchestrator
        );
        AiChatRequest request = new AiChatRequest();
        request.setQuery("hello");

        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(agentService.createCancelToken(eq("user-123"), anyString())).thenReturn(new CancellationToken());
        doThrow(new RejectedExecutionException("full"))
                .when(executorService).execute(any(Runnable.class));

        assertThatThrownBy(() -> controller.chatStream(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(503));

        verify(agentService).cancelRequest(eq("user-123"), anyString());
    }

    @Test
    void chatStream_completionCancelsRequestToken() {
        AtomicReference<TestSseEmitter> createdEmitter = new AtomicReference<>();
        AiController controller = new AiController(
            aiService, securityUtils, traceRepository, new ObjectMapper(), executorService,
            ragFeedbackService, smartSuggestionService, noteRepository, agentService, ragService,
            chatOrchestrator,
            timeout -> {
                TestSseEmitter emitter = new TestSseEmitter(timeout);
                createdEmitter.set(emitter);
                return emitter;
            }
        );
        AiChatRequest request = new AiChatRequest();
        request.setQuery("hello");
        CancellationToken token = new CancellationToken();

        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(agentService.createCancelToken(eq("user-123"), anyString())).thenReturn(token);

        SseEmitter emitter = controller.chatStream(request);
        emitter.complete();

        assertThat(emitter).isSameAs(createdEmitter.get());
        assertThat(token.isCancelled()).isTrue();
    }

    @Test
    void chatStream_clientErrorCancelsRequestToken() {
        AtomicReference<TestSseEmitter> createdEmitter = new AtomicReference<>();
        AiController controller = new AiController(
            aiService, securityUtils, traceRepository, new ObjectMapper(), executorService,
            ragFeedbackService, smartSuggestionService, noteRepository, agentService, ragService,
            chatOrchestrator,
            timeout -> {
                TestSseEmitter emitter = new TestSseEmitter(timeout);
                createdEmitter.set(emitter);
                return emitter;
            }
        );
        AiChatRequest request = new AiChatRequest();
        request.setQuery("hello");
        CancellationToken token = new CancellationToken();

        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(agentService.createCancelToken(eq("user-123"), anyString())).thenReturn(token);

        controller.chatStream(request);
        createdEmitter.get().triggerError(new IllegalStateException("client disconnected"));

        assertThat(token.isCancelled()).isTrue();
    }

    @Test
    void chatStream_sendFailureCancelsAndSuppressesLaterCallbacks() {
        AtomicReference<TestSseEmitter> createdEmitter = new AtomicReference<>();
        AiController controller = new AiController(
            aiService, securityUtils, traceRepository, new ObjectMapper(), executorService,
            ragFeedbackService, smartSuggestionService, noteRepository, agentService, ragService,
            chatOrchestrator,
            timeout -> {
                TestSseEmitter emitter = new TestSseEmitter(timeout);
                emitter.failOnFirstSend();
                createdEmitter.set(emitter);
                return emitter;
            }
        );
        AiChatRequest request = new AiChatRequest();
        request.setQuery("hello");
        CancellationToken token = new CancellationToken();

        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(agentService.createCancelToken(eq("user-123"), anyString())).thenReturn(token);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));
        doAnswer(invocation -> {
            StreamCallback callback = invocation.getArgument(4);
            callback.onToken("partial");
            callback.onComplete(new AiChatResponse("done", Map.of()));
            return null;
        }).when(chatOrchestrator).chatStream(
                eq("hello"),
                any(),
                eq("user-123"),
                anyString(),
                any(StreamCallback.class));

        controller.chatStream(request);

        TestSseEmitter emitter = createdEmitter.get();
        assertThat(token.isCancelled()).isTrue();
        assertThat(emitter.sendAttempts()).isEqualTo(1);
        assertThat(emitter.completeCalls()).isZero();
        assertThat(emitter.completedWithError()).isInstanceOf(IOException.class);
    }

    @Test
    void chatStream_recordsSseLifecycleMetrics() {
        AtomicReference<TestSseEmitter> createdEmitter = new AtomicReference<>();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ChatMetrics chatMetrics = new ChatMetrics(meterRegistry);
        AiController controller = new AiController(
            aiService, securityUtils, traceRepository, new ObjectMapper(), executorService,
            ragFeedbackService, smartSuggestionService, noteRepository, agentService, ragService,
            chatOrchestrator,
            timeout -> {
                TestSseEmitter emitter = new TestSseEmitter(timeout);
                createdEmitter.set(emitter);
                return emitter;
            },
            chatMetrics
        );
        AiChatRequest request = new AiChatRequest();
        request.setQuery("hello");

        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(agentService.createCancelToken(eq("user-123"), anyString())).thenReturn(new CancellationToken());
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));
        doAnswer(invocation -> {
            StreamCallback callback = invocation.getArgument(4);
            callback.onToken("partial");
            callback.onComplete(new AiChatResponse("done", Map.of()));
            return null;
        }).when(chatOrchestrator).chatStream(
                eq("hello"),
                any(),
                eq("user-123"),
                anyString(),
                any(StreamCallback.class));

        controller.chatStream(request);

        assertThat(createdEmitter.get()).isNotNull();
        assertThat(meterRegistry.counter("chat.streams.opened", "transport", "post").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("chat.stream.events", "event", "heartbeat").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("chat.stream.events", "event", "token").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("chat.stream.events", "event", "complete").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("chat.streams.closed", "reason", "complete").count()).isEqualTo(1.0);
    }

    @Test
    void chatStream_schedulesAndCancelsPeriodicHeartbeat() {
        AtomicReference<TestSseEmitter> createdEmitter = new AtomicReference<>();
        ScheduledExecutorService heartbeatScheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> heartbeatFuture = mock(ScheduledFuture.class);
        doReturn(heartbeatFuture).when(heartbeatScheduler).scheduleAtFixedRate(
                any(Runnable.class),
                eq(15L),
                eq(15L),
                eq(TimeUnit.SECONDS));
        AiController controller = new AiController(
            aiService, securityUtils, traceRepository, new ObjectMapper(), executorService,
            ragFeedbackService, smartSuggestionService, noteRepository, agentService, ragService,
            chatOrchestrator,
            timeout -> {
                TestSseEmitter emitter = new TestSseEmitter(timeout);
                createdEmitter.set(emitter);
                return emitter;
            },
            null,
            heartbeatScheduler
        );
        AiChatRequest request = new AiChatRequest();
        request.setQuery("hello");

        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(agentService.createCancelToken(eq("user-123"), anyString())).thenReturn(new CancellationToken());
        doAnswer(invocation -> null).when(executorService).execute(any(Runnable.class));

        SseEmitter emitter = controller.chatStream(request);
        emitter.complete();

        assertThat(createdEmitter.get()).isNotNull();
        verify(heartbeatScheduler).scheduleAtFixedRate(
                any(Runnable.class),
                eq(15L),
                eq(15L),
                eq(TimeUnit.SECONDS));
        verify(heartbeatFuture).cancel(false);
    }

    private static final class TestSseEmitter extends SseEmitter {
        private Runnable completionCallback;
        private Consumer<Throwable> errorCallback;
        private boolean failOnFirstSend;
        private int sendAttempts;
        private int completeCalls;
        private Throwable completedWithError;

        private TestSseEmitter(Long timeout) {
            super(timeout);
        }

        private void failOnFirstSend() {
            this.failOnFirstSend = true;
        }

        private int sendAttempts() {
            return sendAttempts;
        }

        private int completeCalls() {
            return completeCalls;
        }

        private Throwable completedWithError() {
            return completedWithError;
        }

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            sendAttempts++;
            if (failOnFirstSend && sendAttempts == 1) {
                throw new IOException("client disconnected");
            }
            super.send(builder);
        }

        @Override
        public synchronized void onCompletion(Runnable callback) {
            super.onCompletion(callback);
            this.completionCallback = callback;
        }

        @Override
        public synchronized void onError(Consumer<Throwable> callback) {
            super.onError(callback);
            this.errorCallback = callback;
        }

        @Override
        public synchronized void complete() {
            completeCalls++;
            if (completionCallback != null) {
                completionCallback.run();
            }
            super.complete();
        }

        @Override
        public synchronized void completeWithError(Throwable ex) {
            this.completedWithError = ex;
            if (errorCallback != null) {
                errorCallback.accept(ex);
            }
        }

        private void triggerError(Throwable throwable) {
            if (errorCallback != null) {
                errorCallback.accept(throwable);
            }
        }
    }
}
