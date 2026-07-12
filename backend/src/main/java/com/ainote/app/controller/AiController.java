package com.ainote.app.controller;

import com.ainote.app.agent.CancellationToken;
import com.ainote.app.model.ActionFeedbackRequest;
import com.ainote.app.model.AgentTraceResponse;
import com.ainote.app.model.AiChatRequest;
import com.ainote.app.model.AiChatResponse;
import com.ainote.app.model.AiNoteRequest;
import com.ainote.app.model.AiResponse;
import com.ainote.app.model.ChatSaveRequest;
import com.ainote.app.model.ChatHistoryPage;
import com.ainote.app.model.ClassificationResponse;
import com.ainote.app.model.ExtractedSchedule;
import com.ainote.app.model.GenerateCanvasRequest;
import com.ainote.app.model.RagFeedbackRequest;
import com.ainote.app.repository.AgentTraceRepository;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.AiService;
import com.ainote.app.service.chat.ChatMetrics;
import com.ainote.app.service.chat.ChatOrchestrator;
import com.ainote.app.service.chat.StreamCallback;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI 功能", description = "AI 相关功能接口，包括对话、总结、提取行动项等")
@SecurityRequirement(name = "Bearer Authentication")
public class AiController {
    private static final Logger log = LoggerFactory.getLogger(AiController.class);
    private static final long SSE_TIMEOUT_BUFFER_SECONDS = 30L;

    private final AiService aiService;
    private final SecurityUtils securityUtils;
    private final AgentTraceRepository traceRepository;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final com.ainote.app.service.RagFeedbackService ragFeedbackService;
    private final com.ainote.app.service.SmartSuggestionService smartSuggestionService;
    private final com.ainote.app.repository.NoteRepository noteRepository;
    private final com.ainote.app.service.AgentService agentService;
    private final com.ainote.app.service.LangChain4jRagService ragService;
    private final ChatOrchestrator chatOrchestrator;
    private final SseEmitterFactory sseEmitterFactory;
    private final ChatMetrics chatMetrics;
    private final ScheduledExecutorService heartbeatScheduler;

    @Value("${app.agent.timeout-seconds:300}")
    private int agentTimeoutSeconds = 300;

    @Value("${app.sse.retry-ms:3000}")
    private long sseRetryMillis = 3000;

    @Value("${app.sse.heartbeat-interval-seconds:15}")
    private long sseHeartbeatIntervalSeconds = 15;

    @Autowired
    public AiController(AiService aiService, SecurityUtils securityUtils,
                        AgentTraceRepository traceRepository, ObjectMapper objectMapper,
                        @Qualifier("sseExecutor") ExecutorService executorService,
                        @Qualifier("sseHeartbeatScheduler") ScheduledExecutorService heartbeatScheduler,
                        com.ainote.app.service.RagFeedbackService ragFeedbackService,
                        com.ainote.app.service.SmartSuggestionService smartSuggestionService,
                        com.ainote.app.repository.NoteRepository noteRepository,
                        com.ainote.app.service.AgentService agentService,
                        com.ainote.app.service.LangChain4jRagService ragService,
                        ChatOrchestrator chatOrchestrator,
                        ChatMetrics chatMetrics) {
        this(aiService, securityUtils, traceRepository, objectMapper, executorService,
                ragFeedbackService, smartSuggestionService, noteRepository, agentService,
                ragService, chatOrchestrator, SseEmitter::new, chatMetrics, heartbeatScheduler);
    }

    AiController(AiService aiService, SecurityUtils securityUtils,
                 AgentTraceRepository traceRepository, ObjectMapper objectMapper,
                 ExecutorService executorService,
                 com.ainote.app.service.RagFeedbackService ragFeedbackService,
                 com.ainote.app.service.SmartSuggestionService smartSuggestionService,
                 com.ainote.app.repository.NoteRepository noteRepository,
                 com.ainote.app.service.AgentService agentService,
                 com.ainote.app.service.LangChain4jRagService ragService,
                 ChatOrchestrator chatOrchestrator) {
        this(aiService, securityUtils, traceRepository, objectMapper, executorService,
                ragFeedbackService, smartSuggestionService, noteRepository, agentService,
                ragService, chatOrchestrator, SseEmitter::new, null, null);
    }

    AiController(AiService aiService, SecurityUtils securityUtils,
                 AgentTraceRepository traceRepository, ObjectMapper objectMapper,
                 ExecutorService executorService,
                 com.ainote.app.service.RagFeedbackService ragFeedbackService,
                 com.ainote.app.service.SmartSuggestionService smartSuggestionService,
                 com.ainote.app.repository.NoteRepository noteRepository,
                 com.ainote.app.service.AgentService agentService,
                 com.ainote.app.service.LangChain4jRagService ragService,
                 ChatOrchestrator chatOrchestrator,
                 SseEmitterFactory sseEmitterFactory) {
        this(aiService, securityUtils, traceRepository, objectMapper, executorService,
                ragFeedbackService, smartSuggestionService, noteRepository, agentService,
                ragService, chatOrchestrator, sseEmitterFactory, null, null);
    }

    AiController(AiService aiService, SecurityUtils securityUtils,
                 AgentTraceRepository traceRepository, ObjectMapper objectMapper,
                 ExecutorService executorService,
                 com.ainote.app.service.RagFeedbackService ragFeedbackService,
                 com.ainote.app.service.SmartSuggestionService smartSuggestionService,
                 com.ainote.app.repository.NoteRepository noteRepository,
                 com.ainote.app.service.AgentService agentService,
                 com.ainote.app.service.LangChain4jRagService ragService,
                 ChatOrchestrator chatOrchestrator,
                 SseEmitterFactory sseEmitterFactory,
                 ChatMetrics chatMetrics) {
        this(aiService, securityUtils, traceRepository, objectMapper, executorService,
                ragFeedbackService, smartSuggestionService, noteRepository, agentService,
                ragService, chatOrchestrator, sseEmitterFactory, chatMetrics, null);
    }

    AiController(AiService aiService, SecurityUtils securityUtils,
                 AgentTraceRepository traceRepository, ObjectMapper objectMapper,
                 ExecutorService executorService,
                 com.ainote.app.service.RagFeedbackService ragFeedbackService,
                 com.ainote.app.service.SmartSuggestionService smartSuggestionService,
                 com.ainote.app.repository.NoteRepository noteRepository,
                 com.ainote.app.service.AgentService agentService,
                 com.ainote.app.service.LangChain4jRagService ragService,
                 ChatOrchestrator chatOrchestrator,
                 SseEmitterFactory sseEmitterFactory,
                 ChatMetrics chatMetrics,
                 ScheduledExecutorService heartbeatScheduler) {
        this.aiService = aiService;
        this.securityUtils = securityUtils;
        this.traceRepository = traceRepository;
        this.objectMapper = objectMapper;
        this.executorService = executorService;
        this.ragFeedbackService = ragFeedbackService;
        this.smartSuggestionService = smartSuggestionService;
        this.noteRepository = noteRepository;
        this.agentService = agentService;
        this.ragService = ragService;
        this.chatOrchestrator = chatOrchestrator;
        this.sseEmitterFactory = sseEmitterFactory;
        this.chatMetrics = chatMetrics;
        this.heartbeatScheduler = heartbeatScheduler;
    }

    long calculateSseTimeoutMillis() {
        return (agentTimeoutSeconds + SSE_TIMEOUT_BUFFER_SECONDS) * 1000L;
    }

    @GetMapping("/chat/history")
    @Operation(summary = "获取对话历史", description = "分页获取当前用户 AI 对话历史记录")
    public ResponseEntity<ChatHistoryPage> getChatHistory(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String before) {
        String userId = securityUtils.getCurrentUserId();
        ChatHistoryPage history = aiService.getChatHistoryPage(userId, limit, before);
        return ResponseEntity.ok(history);
    }

    @DeleteMapping("/chat/history")
    @Operation(summary = "清除对话记忆", description = "清除当前用户的 Agent 对话记忆，解决上下文污染问题")
    public ResponseEntity<Void> clearChatMemory() {
        String userId = securityUtils.getCurrentUserId();
        aiService.clearChatMemory(userId);
        return ResponseEntity.ok().build();
    }



    @PostMapping("/chat/save")
    @Operation(summary = "保存对话消息", description = "保存意图识别直接回复的对话消息到历史记录（不经过 Agent 时使用）")
    public ResponseEntity<Void> saveChatMessages(@Valid @RequestBody ChatSaveRequest body) {
        String userId = securityUtils.getCurrentUserId();
        String userMsg = body.getUserMessage();
        String aiReply = body.getAiReply();
        aiService.saveChatTurn(userId, userMsg, aiReply);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/chat")
    @Operation(summary = "AI 对话", description = "与 AI 进行对话，支持基于笔记内容的问答和多轮对话记忆，返回结果包含引用来源")
    public ResponseEntity<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                chatOrchestrator.chat(request.getQuery(), request.getNoteIds(), userId));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "AI 流式对话", description = "与 AI 进行流式对话，实时返回响应内容（SSE）")
    public SseEmitter chatStream(@Valid @RequestBody AiChatRequest request) {
        return openChatStream(request, "post");
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "AI EventSource 流式对话", description = "使用 GET 形式订阅 AI 流式对话，兼容标准 EventSource")
    public SseEmitter chatStreamEventSource(
            @RequestParam String query,
            @RequestParam(required = false) List<String> noteIds) {
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query is required");
        }
        AiChatRequest request = new AiChatRequest();
        request.setQuery(query);
        request.setMessage(query);
        request.setNoteIds(noteIds == null ? List.of() : noteIds);
        return openChatStream(request, "eventsource");
    }

    private SseEmitter openChatStream(AiChatRequest request, String transport) {
        SseEmitter emitter = sseEmitterFactory.create(calculateSseTimeoutMillis());

        String userId = securityUtils.getCurrentUserId();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(SecurityContextHolder.getContext().getAuthentication());

        String requestId = UUID.randomUUID().toString();
        CancellationToken cancelToken = agentService.createCancelToken(userId, requestId);
        SseStreamContext streamContext = new SseStreamContext(requestId, cancelToken);
        emitter.onCompletion(() -> cancelStream(streamContext, "client_completion"));
        emitter.onTimeout(() -> cancelStream(streamContext, "timeout"));
        emitter.onError(e -> cancelStream(streamContext, "client_error"));
        recordStreamOpened(transport);
        sendHeartbeat(emitter, streamContext);
        scheduleHeartbeat(emitter, streamContext);

        try {
            Runnable streamTask = () -> {
                try {
                    chatOrchestrator.chatStream(request.getQuery(), request.getNoteIds(), userId, requestId, new StreamCallback() {
                        @Override
                        public void onToken(String token) {
                            sendEvent(emitter, streamContext, "token", token, null);
                        }

                        @Override
                        public void onComplete(AiChatResponse response) {
                            if (sendEvent(emitter, streamContext, "complete", response, null)) {
                                completeStream(emitter, streamContext, "complete");
                            }
                        }

                        @Override
                        public void onError(String error) {
                            if (sendEvent(emitter, streamContext, "error", error, null)) {
                                completeStream(emitter, streamContext, "error");
                            }
                        }

                        @Override
                        public void onProgress(String step, String detail) {
                            Map<String, String> progress = new LinkedHashMap<>();
                            progress.put("step", step);
                            progress.put("detail", detail);
                            sendEvent(emitter, streamContext, "progress", progress, MediaType.APPLICATION_JSON);
                        }
                    });
                } catch (Exception e) {
                    completeStreamWithError(emitter, streamContext, e, "orchestrator_error");
                }
            };
            executorService.execute(new DelegatingSecurityContextRunnable(streamTask, securityContext));
        } catch (RejectedExecutionException e) {
            agentService.cancelRequest(userId, requestId);
            recordStreamRejected(transport);
            completeStreamWithError(emitter, streamContext, e, "rejected");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI stream is busy", e);
        }

        return emitter;
    }

    private void scheduleHeartbeat(SseEmitter emitter, SseStreamContext streamContext) {
        if (heartbeatScheduler == null || sseHeartbeatIntervalSeconds <= 0 || streamContext.closed.get()) {
            return;
        }
        ScheduledFuture<?> future = heartbeatScheduler.scheduleAtFixedRate(
                () -> sendHeartbeat(emitter, streamContext),
                sseHeartbeatIntervalSeconds,
                sseHeartbeatIntervalSeconds,
                TimeUnit.SECONDS);
        streamContext.heartbeatFuture.set(future);
        if (streamContext.closed.get()) {
            future.cancel(false);
        }
    }

    private boolean sendHeartbeat(SseEmitter emitter, SseStreamContext streamContext) {
        return sendEvent(emitter, streamContext, "heartbeat", "ping", null);
    }

    private boolean sendEvent(SseEmitter emitter, SseStreamContext streamContext,
                              String eventName, Object data, MediaType mediaType) {
        if (streamContext.closed.get()) {
            return false;
        }
        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .id(streamContext.nextEventId())
                .reconnectTime(sseRetryMillis)
                .name(eventName);
        if (mediaType == null) {
            event.data(data);
        } else {
            event.data(data, mediaType);
        }
        synchronized (streamContext.sendLock) {
            if (streamContext.closed.get()) {
                return false;
            }
            try {
                emitter.send(event);
                recordStreamEvent(eventName);
                return true;
            } catch (IOException | IllegalStateException e) {
                recordStreamSendError(e instanceof IOException ? "io" : "illegal_state");
                completeStreamWithError(emitter, streamContext, e, "send_error");
                return false;
            }
        }
    }

    private void completeStream(SseEmitter emitter, SseStreamContext streamContext, String reason) {
        if (streamContext.closed.compareAndSet(false, true)) {
            cancelHeartbeat(streamContext);
            streamContext.cancelToken.cancel();
            recordStreamClosed(reason);
            emitter.complete();
        }
    }

    private void completeStreamWithError(SseEmitter emitter, SseStreamContext streamContext,
                                         Throwable error, String reason) {
        if (streamContext.closed.compareAndSet(false, true)) {
            cancelHeartbeat(streamContext);
            streamContext.cancelToken.cancel();
            recordStreamClosed(reason);
            emitter.completeWithError(error);
        }
    }

    private void cancelStream(SseStreamContext streamContext, String reason) {
        if (streamContext.closed.compareAndSet(false, true)) {
            cancelHeartbeat(streamContext);
            streamContext.cancelToken.cancel();
            recordStreamClosed(reason);
        }
    }

    private void cancelHeartbeat(SseStreamContext streamContext) {
        ScheduledFuture<?> future = streamContext.heartbeatFuture.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void recordStreamOpened(String transport) {
        if (chatMetrics != null) {
            chatMetrics.recordStreamOpened(transport);
        }
    }

    private void recordStreamEvent(String eventName) {
        if (chatMetrics != null) {
            chatMetrics.recordStreamEvent(eventName);
        }
    }

    private void recordStreamClosed(String reason) {
        if (chatMetrics != null) {
            chatMetrics.recordStreamClosed(reason);
        }
    }

    private void recordStreamSendError(String reason) {
        if (chatMetrics != null) {
            chatMetrics.recordStreamSendError(reason);
        }
    }

    private void recordStreamRejected(String transport) {
        if (chatMetrics != null) {
            chatMetrics.recordStreamRejected(transport);
        }
    }

    @GetMapping("/spirit/greeting")
    @Operation(summary = "AI 问候语", description = "获取随机的 AI 问候语")
    public ResponseEntity<String> spiritGreeting() {
        return ResponseEntity.ok(aiService.spiritGreeting());
    }

    @GetMapping("/suggestions")
    @Operation(summary = "智能建议", description = "获取当前用户的智能建议卡片数据")
    public ResponseEntity<List<Map<String, Object>>> getSuggestions() {
        try {
            String userId = securityUtils.getCurrentUserId();
            var suggestions = smartSuggestionService.generateSuggestions(userId);
            List<Map<String, Object>> cards = suggestions.stream().map(s -> {
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("type", s.type().name());
                card.put("message", s.message());
                card.put("params", s.params());
                card.put("priority", s.priority());
                // 转成前端 CardPayload 格式
                card.put("card", buildCardPayload(s));
                return card;
            }).toList();
            return ResponseEntity.ok(cards);
        } catch (Exception e) {
            log.warn("Failed to generate suggestions", e);
            return ResponseEntity.ok(List.of()); // 建议获取失败不影响用户
        }
    }

    private Map<String, Object> buildCardPayload(
            com.ainote.app.service.SmartSuggestionService.Suggestion s) {
        Map<String, Object> card = new LinkedHashMap<>();
        switch (s.type()) {
            case OVERDUE_SCHEDULE -> {
                card.put("kind", "suggestion");
                card.put("suggestionKind", "expired");
                card.put("text", s.message());
                card.put("buttons", List.of(
                    Map.of("label", "标记完成", "variant", "primary",
                           "action", "COMPLETE_SCHEDULE:" + s.params().getOrDefault("scheduleId", "")),
                    Map.of("label", "延后到明天", "action", "POSTPONE_SCHEDULE:" + s.params().getOrDefault("scheduleId", "")),
                    Map.of("label", "忽略", "variant", "subtle", "action", "dismiss")
                ));
            }
            case REVIEW_NOTE -> {
                card.put("kind", "suggestion");
                card.put("suggestionKind", "review");
                card.put("text", s.message());
                card.put("buttons", List.of(
                    Map.of("label", "打开笔记", "variant", "primary",
                           "action", "OPEN_NOTE:" + s.params().getOrDefault("noteId", "")),
                    Map.of("label", "创建复习日程", "action", "CREATE_REVIEW_SCHEDULE:" + s.params().getOrDefault("noteId", "")),
                    Map.of("label", "忽略", "variant", "subtle", "action", "dismiss")
                ));
            }
            case MERGE_NOTES -> {
                card.put("kind", "suggestion");
                card.put("suggestionKind", "merge");
                card.put("text", s.message());
                card.put("buttons", List.of(
                    Map.of("label", "预览合并", "variant", "primary",
                           "action", "MERGE_NOTES:" + s.params().getOrDefault("noteId1", "") + "," + s.params().getOrDefault("noteId2", "")),
                    Map.of("label", "不用了", "variant", "subtle", "action", "dismiss")
                ));
            }
            case ORGANIZE_NOTES -> {
                card.put("kind", "suggestion");
                card.put("suggestionKind", "review");
                card.put("text", s.message());
                card.put("buttons", List.of(
                    Map.of("label", "帮我分类", "variant", "primary", "action", "AUTO_CLASSIFY"),
                    Map.of("label", "忽略", "variant", "subtle", "action", "dismiss")
                ));
            }
            case ADD_TAGS -> {
                card.put("kind", "suggestion");
                card.put("suggestionKind", "review");
                card.put("text", s.message());
                card.put("buttons", List.of(
                    Map.of("label", "自动打标签", "variant", "primary", "action", "AUTO_TAG"),
                    Map.of("label", "忽略", "variant", "subtle", "action", "dismiss")
                ));
            }
            case EXTRACT_SCHEDULE -> {
                card.put("kind", "suggestion");
                card.put("suggestionKind", "expired");
                card.put("text", s.message());
                card.put("buttons", List.of(
                    Map.of("label", "提取日程", "variant", "primary",
                           "action", "EXTRACT_SCHEDULE:" + s.params().getOrDefault("noteId", "")),
                    Map.of("label", "忽略", "variant", "subtle", "action", "dismiss")
                ));
            }
        }
        return card;
    }

    @PostMapping("/spirit/suggest-tags")
    @Operation(summary = "智能标签建议", description = "根据笔记内容智能推荐标签")
    public ResponseEntity<String> spiritSuggestTags(@Valid @RequestBody AiNoteRequest request) {
        return ResponseEntity.ok(aiService.spiritSuggestTags(request.getNoteId()));
    }

    @PostMapping("/classify")
    @Operation(summary = "智能分类笔记", description = "AI 分析所有笔记内容，建议将笔记分类到合适的文件夹")
    public ResponseEntity<ClassificationResponse> classifyNotes() {
        return ResponseEntity.ok(aiService.classifyNotes());
    }

    @PostMapping("/extract-schedules")
    @Operation(summary = "提取日程", description = "从笔记内容中智能提取日程信息，支持识别时间、重复规则等")
    public ResponseEntity<ExtractedSchedule.ExtractResponse> extractSchedules(
            @Parameter(description = "笔记 ID") @Valid @RequestBody AiNoteRequest request) {
        return ResponseEntity.ok(aiService.extractSchedules(request.getNoteId()));
    }

    @GetMapping("/traces")
    @Operation(summary = "获取 Agent 调用追踪", description = "获取当前用户的 Agent 调用追踪记录，按时间倒序")
    public ResponseEntity<List<AgentTraceResponse>> getTraces(
            @Parameter(description = "返回条数，默认 50") @RequestParam(defaultValue = "50") int limit,
            @Parameter(description = "开始时间") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @Parameter(description = "结束时间") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @Parameter(description = "模型名称") @RequestParam(required = false) String model) {
        String userId = securityUtils.getCurrentUserId();
        List<AgentTraceResponse> traces = traceRepository.findFilteredTraces(
                        userId,
                        start,
                        end,
                        (model == null || model.isBlank()) ? null : model,
                        PageRequest.of(0, limit))
                .stream()
                .map(AgentTraceResponse::from)
                .toList();
        return ResponseEntity.ok(traces);
    }

    @GetMapping("/traces/stats")
    @Operation(summary = "获取 Agent 使用统计", description = "获取当前用户的 Agent 调用次数和 token 消耗统计")
    public ResponseEntity<Map<String, Object>> getTraceStats() {
        String userId = securityUtils.getCurrentUserId();
        long totalCalls = traceRepository.countByUserId(userId);
        long totalTokens = traceRepository.sumTotalTokensByUserId(userId);

        // 今日统计
        java.time.LocalDateTime todayStart = java.time.LocalDate.now().atStartOfDay();
        java.time.LocalDateTime todayEnd = todayStart.plusDays(1);
        long todayCalls = traceRepository.countByUserIdAndCreatedAtBetween(userId, todayStart, todayEnd);
        long todayTokens = traceRepository.sumTotalTokensByUserIdAndDateRange(userId, todayStart, todayEnd);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalCalls", totalCalls);
        stats.put("totalTokens", totalTokens);
        stats.put("todayCalls", todayCalls);
        stats.put("todayTokens", todayTokens);
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/action-feedback")
    @Operation(summary = "操作反馈", description = "用户确认或拒绝 PENDING_ACTION 后，将决策回传给 Agent 继续对话")
    public ResponseEntity<AiChatResponse> actionFeedback(@Valid @RequestBody ActionFeedbackRequest body) {
        String userId = securityUtils.getCurrentUserId();
        String actionJson = body.getActionJson();
        boolean confirmed = Boolean.TRUE.equals(body.getConfirmed());
        String feedback = body.getFeedback();
        AiChatResponse response = agentService.confirmAction(userId, actionJson, confirmed, feedback);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/rag-feedback")
    @Operation(summary = "提交 RAG 反馈", description = "用户对搜索结果的反馈，用于自适应调整检索阈值")
    public ResponseEntity<Void> submitRagFeedback(@Valid @RequestBody RagFeedbackRequest body) {
        String userId = securityUtils.getCurrentUserId();
        String query = body.getQuery();
        String resultNoteId = body.getResultNoteId();
        double similarityScore = body.getSimilarityScore() != null ? body.getSimilarityScore() : 0.0;
        String feedbackType = body.getFeedbackType() != null ? body.getFeedbackType() : "CLICK";

        ragFeedbackService.recordFeedback(userId, query, resultNoteId, similarityScore, feedbackType);
        return ResponseEntity.ok().build();
    }

    /**
     * AI 生成画布 — 基于 embedding 向量相似度，自动聚类笔记并生成关系连线
     * 不依赖 LLM，速度快、结果稳定、不消耗 token
     */
    @PostMapping("/generate-canvas")
    @Operation(summary = "AI 生成画布", description = "基于语义相似度分析笔记关系，自动生成画布布局")
    public ResponseEntity<Map<String, Object>> generateCanvas(@Valid @RequestBody(required = false) GenerateCanvasRequest body) {
        try {
            String userId = securityUtils.getCurrentUserId();
            var notes = noteRepository.findByUserIdAndDeletedAtIsNull(userId);
            if (notes.isEmpty()) {
                return ResponseEntity.ok(Map.of("nodes", List.of(), "edges", List.of()));
            }

            int requestedLimit = body != null && body.getLimit() != null ? body.getLimit() : 30;
            int limit = Math.min(notes.size(), requestedLimit);
            var selected = notes.subList(0, limit);
            String[] COLORS = {"#b8452e", "#6b7a5a", "#c9a959", "#6b85a3", "#7a6b58", "#a06b8c"};

            // 1. 计算每篇笔记的 embedding
            float[][] vectors = new float[limit][];
            for (int i = 0; i < limit; i++) {
                var note = selected.get(i);
                String text = (note.getTitle() != null ? note.getTitle() + " " : "") +
                    (note.getContent() != null ? note.getContent().replaceAll("<[^>]*>", "") : "");
                if (text.length() > 500) text = text.substring(0, 500);
                var embedding = ragService.getEmbeddingModel().embed(text).content();
                vectors[i] = embedding.vector();
            }

            // 2. 计算两两余弦相似度，找出高相似度的边
            double EDGE_THRESHOLD = 0.6;
            List<Map<String, Object>> edgeList = new java.util.ArrayList<>();
            double[][] simMatrix = new double[limit][limit];
            for (int i = 0; i < limit; i++) {
                for (int j = i + 1; j < limit; j++) {
                    double sim = cosineSimilarity(vectors[i], vectors[j]);
                    simMatrix[i][j] = sim;
                    simMatrix[j][i] = sim;
                    if (sim >= EDGE_THRESHOLD) {
                        edgeList.add(Map.of(
                            "from", selected.get(i).getId(),
                            "to", selected.get(j).getId(),
                            "label", String.format("相似度 %.0f%%", sim * 100)
                        ));
                    }
                }
            }

            // 3. 简单聚类：贪心合并最相似的笔记到同一组
            int[] groups = new int[limit];
            java.util.Arrays.fill(groups, -1);
            int groupCount = 0;
            for (int i = 0; i < limit; i++) {
                if (groups[i] != -1) continue;
                groups[i] = groupCount;
                for (int j = i + 1; j < limit; j++) {
                    if (groups[j] != -1) continue;
                    if (simMatrix[i][j] >= EDGE_THRESHOLD * 0.9) {
                        groups[j] = groupCount;
                    }
                }
                groupCount++;
            }

            // 4. 力导向布局：同组靠近，不同组分散
            double[][] pos = new double[limit][2];
            // 初始位置按组环形排列
            for (int i = 0; i < limit; i++) {
                double groupAngle = (2 * Math.PI * groups[i]) / Math.max(groupCount, 1);
                double groupCx = 600 + 350 * Math.cos(groupAngle);
                double groupCy = 400 + 250 * Math.sin(groupAngle);
                pos[i][0] = groupCx + (Math.random() - 0.5) * 200;
                pos[i][1] = groupCy + (Math.random() - 0.5) * 150;
            }
            // 简单力导向迭代
            for (int iter = 0; iter < 50; iter++) {
                double[][] forces = new double[limit][2];
                // 斥力（所有节点对）
                for (int i = 0; i < limit; i++) {
                    for (int j = i + 1; j < limit; j++) {
                        double dx = pos[i][0] - pos[j][0];
                        double dy = pos[i][1] - pos[j][1];
                        double dist = Math.max(Math.sqrt(dx * dx + dy * dy), 1);
                        double repulse = 8000 / (dist * dist);
                        forces[i][0] += (dx / dist) * repulse;
                        forces[i][1] += (dy / dist) * repulse;
                        forces[j][0] -= (dx / dist) * repulse;
                        forces[j][1] -= (dy / dist) * repulse;
                    }
                }
                // 引力（相似的节点对）
                for (int i = 0; i < limit; i++) {
                    for (int j = i + 1; j < limit; j++) {
                        if (simMatrix[i][j] < EDGE_THRESHOLD * 0.7) continue;
                        double dx = pos[j][0] - pos[i][0];
                        double dy = pos[j][1] - pos[i][1];
                        double dist = Math.max(Math.sqrt(dx * dx + dy * dy), 1);
                        double attract = dist * simMatrix[i][j] * 0.01;
                        forces[i][0] += (dx / dist) * attract;
                        forces[i][1] += (dy / dist) * attract;
                        forces[j][0] -= (dx / dist) * attract;
                        forces[j][1] -= (dy / dist) * attract;
                    }
                }
                // 应用力
                for (int i = 0; i < limit; i++) {
                    pos[i][0] += Math.max(-20, Math.min(20, forces[i][0]));
                    pos[i][1] += Math.max(-20, Math.min(20, forces[i][1]));
                }
            }

            // 5. 构建节点列表
            String[] groupNames = new String[groupCount];
            for (int g = 0; g < groupCount; g++) groupNames[g] = "主题 " + (char)('A' + g % 26);
            // 用组内第一个笔记的标签或标题作为组名
            for (int i = 0; i < limit; i++) {
                if (groupNames[groups[i]].startsWith("主题 ")) {
                    var note = selected.get(i);
                    if (note.getTags() != null && !note.getTags().isEmpty()) {
                        groupNames[groups[i]] = note.getTags().iterator().next().getName();
                    } else if (note.getTitle() != null && note.getTitle().length() <= 8) {
                        groupNames[groups[i]] = note.getTitle();
                    }
                }
            }

            List<Map<String, Object>> nodeList = new java.util.ArrayList<>();
            for (int i = 0; i < limit; i++) {
                var note = selected.get(i);
                String content = note.getContent() != null ? note.getContent().replaceAll("<[^>]*>", "") : "";
                if (content.length() > 100) content = content.substring(0, 100);
                nodeList.add(Map.of(
                    "noteId", note.getId(),
                    "title", note.getTitle() != null ? note.getTitle() : "无标题",
                    "preview", content,
                    "x", (int) pos[i][0],
                    "y", (int) pos[i][1],
                    "color", COLORS[groups[i] % COLORS.length],
                    "group", groupNames[groups[i]]
                ));
            }

            return ResponseEntity.ok(Map.of("nodes", nodeList, "edges", edgeList));
        } catch (Exception e) {
            log.error("Embedding visualization failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "\u5d4c\u5165\u53ef\u89c6\u5316\u751f\u6210\u5931\u8d25"));
        }
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-10);
    }

    private static final class SseStreamContext {
        private final String requestId;
        private final CancellationToken cancelToken;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicLong eventSequence = new AtomicLong(0);
        private final AtomicReference<ScheduledFuture<?>> heartbeatFuture = new AtomicReference<>();
        private final Object sendLock = new Object();

        private SseStreamContext(String requestId, CancellationToken cancelToken) {
            this.requestId = requestId;
            this.cancelToken = cancelToken;
        }

        private String nextEventId() {
            return requestId + "-" + eventSequence.incrementAndGet();
        }
    }
}

@FunctionalInterface
interface SseEmitterFactory {
    SseEmitter create(long timeoutMillis);
}
