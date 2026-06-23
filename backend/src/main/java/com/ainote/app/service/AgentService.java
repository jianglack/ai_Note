package com.ainote.app.service;

import com.ainote.app.agent.AgentAssistant;
import com.ainote.app.agent.CancellationToken;
import com.ainote.app.agent.ConcurrencyGuard;
import com.ainote.app.agent.budget.TokenBudget;
import com.ainote.app.agent.guardrail.GuardrailMode;
import com.ainote.app.agent.guardrail.InputGuardrail;
import com.ainote.app.agent.guardrail.GuardrailResult;
import com.ainote.app.agent.guardrail.OutputGuardrail;
import com.ainote.app.agent.pending.PendingActionRegistry;
import com.ainote.app.agent.pipeline.GracefulDegradation;
import com.ainote.app.agent.pipeline.ToolAuditLogger;
import com.ainote.app.agent.pipeline.ToolExecutionPipeline;
import com.ainote.app.memory.DeferredMemoryState;
import com.ainote.app.memory.ReliableChatMemoryStore;
import com.ainote.app.model.AiChatResponse;
import com.ainote.app.observability.AgentTraceListener;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.UserMemoryRepository;
import com.ainote.app.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Agent 服务
 * 封装 LangChain4j AgentAssistant 的调用
 * 处理 PENDING_ACTION 机制，将敏感操作返回给前端确认
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AgentAssistant agentAssistant;
    private final NoteRepository noteRepository;
    private final NoteService noteService;
    private final SecurityUtils securityUtils;
    private final UserMemoryRepository userMemoryRepository;
    private final ObjectMapper objectMapper;
    private final PendingActionRegistry pendingActionRegistry;
    private final java.util.concurrent.ExecutorService securityExecutor;
    private final ContextAssembler contextAssembler;
    private final ToolCallAuditor toolCallAuditor;
    private final ReliableChatMemoryStore reliableChatMemoryStore;
    private final MemoryExtractionService memoryExtractionService;
    private final Tracer tracer;
    private final com.ainote.app.agent.tools.ToolLoopDetector toolLoopDetector;
    private final ConcurrencyGuard concurrencyGuard;
    private final TokenBudget tokenBudget;
    private final InputGuardrail inputGuardrail;
    private final OutputGuardrail outputGuardrail;

    /** 用户 → 取消令牌的注册表，SSE 断开时通过此令牌取消 Agent 执行 */
    private final ConcurrentHashMap<String, CancellationToken> cancelTokenRegistry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> cancelTokenKeysByUser = new ConcurrentHashMap<>();

    @Value("${app.agent.timeout-seconds:300}")
    private int agentTimeoutSeconds;

    public AgentService(
            AgentAssistant agentAssistant,
            NoteRepository noteRepository,
            NoteService noteService,
            SecurityUtils securityUtils,
            UserMemoryRepository userMemoryRepository,
            ObjectMapper objectMapper,
            PendingActionRegistry pendingActionRegistry,
            @org.springframework.beans.factory.annotation.Qualifier("securityExecutor")
            java.util.concurrent.ExecutorService securityExecutor,
            ContextAssembler contextAssembler,
            ToolCallAuditor toolCallAuditor,
            ReliableChatMemoryStore reliableChatMemoryStore,
            MemoryExtractionService memoryExtractionService,
            Tracer tracer,
            com.ainote.app.agent.tools.ToolLoopDetector toolLoopDetector,
            ConcurrencyGuard concurrencyGuard,
            TokenBudget tokenBudget,
            InputGuardrail inputGuardrail,
            OutputGuardrail outputGuardrail
    ) {
        this.agentAssistant = agentAssistant;
        this.noteRepository = noteRepository;
        this.noteService = noteService;
        this.securityUtils = securityUtils;
        this.userMemoryRepository = userMemoryRepository;
        this.objectMapper = objectMapper;
        this.pendingActionRegistry = pendingActionRegistry;
        this.securityExecutor = securityExecutor;
        this.contextAssembler = contextAssembler;
        this.toolCallAuditor = toolCallAuditor;
        this.reliableChatMemoryStore = reliableChatMemoryStore;
        this.memoryExtractionService = memoryExtractionService;
        this.tracer = tracer;
        this.toolLoopDetector = toolLoopDetector;
        this.concurrencyGuard = concurrencyGuard;
        this.tokenBudget = tokenBudget;
        this.inputGuardrail = inputGuardrail;
        this.outputGuardrail = outputGuardrail;
    }

    private static class AgentInvocationResult {
        private final String rawResponse;
        private final List<ToolAuditLogger.ToolAuditEntry> transcript;
        private boolean memoryPersisted = true;
        private String memoryFlushError;

        AgentInvocationResult(String rawResponse, List<ToolAuditLogger.ToolAuditEntry> transcript) {
            this.rawResponse = rawResponse;
            this.transcript = transcript;
        }

        String rawResponse() {
            return rawResponse;
        }

        List<ToolAuditLogger.ToolAuditEntry> transcript() {
            return transcript;
        }

        boolean isMemoryPersisted() {
            return memoryPersisted;
        }

        void setMemoryPersisted(boolean memoryPersisted) {
            this.memoryPersisted = memoryPersisted;
        }

        String memoryFlushError() {
            return memoryFlushError;
        }

        void setMemoryFlushError(String memoryFlushError) {
            this.memoryFlushError = memoryFlushError;
        }
    }

    /**
     * 创建取消令牌并注册。由 AiController 在 SSE 建立时调用。
     */
    public CancellationToken createCancelToken(String userId) {
        return createCancelToken(userId, UUID.randomUUID().toString());
    }

    public CancellationToken createCancelToken(String userId, String requestId) {
        CancellationToken token = new CancellationToken();
        String key = cancelTokenKey(userId, requestId);
        cancelTokenRegistry.put(key, token);
        cancelTokenKeysByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(key);
        return token;
    }

    public void cancelCurrentRequest(String userId) {
        Set<String> keys = cancelTokenKeysByUser.get(userId);
        if (keys != null) {
            for (String key : keys) {
                CancellationToken token = cancelTokenRegistry.get(key);
                if (token != null) {
                    token.cancel();
                }
            }
        }
    }

    /**
     * 移除取消令牌。由 Agent 调用结束后清理。
     */
    private CancellationToken getOrCreateCancelToken(String userId, String requestId) {
        String key = cancelTokenKey(userId, requestId);
        return cancelTokenRegistry.computeIfAbsent(key, ignored -> {
            cancelTokenKeysByUser.computeIfAbsent(userId, user -> ConcurrentHashMap.newKeySet()).add(key);
            return new CancellationToken();
        });
    }

    private void removeCancelToken(String userId, String requestId) {
        String key = cancelTokenKey(userId, requestId);
        cancelTokenRegistry.remove(key);
        Set<String> keys = cancelTokenKeysByUser.get(userId);
        if (keys != null) {
            keys.remove(key);
            if (keys.isEmpty()) {
                cancelTokenKeysByUser.remove(userId, keys);
            }
        }
    }

    private String cancelTokenKey(String userId, String requestId) {
        return userId + ":" + requestId;
    }

    /**
     * 清除用户的对话记忆
     * 清空前先生成情节摘要保留要点
     */
    public void clearMemory(String userId) {
        log.info("Clearing memory for user: {}", userId);

        // 清空前先生成情节摘要
        try {
            memoryExtractionService.generateEpisodicSummary(userId);
        } catch (Exception e) {
            log.warn("Failed to generate episodic summary before clearing: {}", e.getMessage());
        }

        userMemoryRepository.deleteByUserId(userId);
    }

    /**
     * Agent 对话
     */
    public AiChatResponse chat(String query, List<String> noteIds) {
        return chat(query, noteIds, securityUtils.getCurrentUserId());
    }

    /**
     * Agent 对话（带用户ID参数，用于异步调用场景）
     */
    public AiChatResponse chat(String query, List<String> noteIds, String userId) {
        return doChat(query, noteIds, userId, userId, GuardrailMode.USER_INPUT, null, null);
    }

    public AiChatResponse chatAlreadyChecked(String query, List<String> noteIds, String userId) {
        return doChat(query, noteIds, userId, userId, GuardrailMode.ALREADY_CHECKED_USER_INPUT, null, null);
    }

    public AiChatResponse chatTrustedSystemPrompt(
            String query,
            List<String> noteIds,
            String memoryId,
            String actorUserId,
            String source,
            String reason
    ) {
        requireNonBlank(memoryId, "memoryId");
        requireNonBlank(actorUserId, "actorUserId");
        requireNonBlank(source, "source");
        requireNonBlank(reason, "reason");
        log.info("Bypassing input guardrail for trusted system prompt: source={}, reason={}, actorUserId={}, memoryId={}",
                source, reason, actorUserId, memoryId);
        return doChat(query, noteIds, memoryId, actorUserId, GuardrailMode.TRUSTED_SYSTEM_PROMPT, source, reason);
    }

    private AiChatResponse doChat(
            String query,
            List<String> noteIds,
            String memoryId,
            String actorUserId,
            GuardrailMode guardrailMode,
            String source,
            String reason
    ) {
        log.info("=== Agent Chat Request === Query: {}, NoteIds: {}", query, noteIds);

        // 输入安全护栏
        if (guardrailMode == GuardrailMode.USER_INPUT) {
            GuardrailResult inputCheck = inputGuardrail.check(query);
            if (!inputCheck.passed()) {
                return new AiChatResponse(inputCheck.reason(), new HashMap<>(), (String) null);
            }
        }

        AiChatResponse directPendingAction = tryBuildSelectedNoteDeleteConfirmation(query, noteIds, actorUserId);
        if (directPendingAction != null) {
            log.info("Returning deterministic selected-note delete confirmation for user={}, noteIds={}", actorUserId, noteIds);
            return directPendingAction;
        }

        // 并发控制：每用户同时只能有一个 Agent 调用
        if (!concurrencyGuard.tryAcquire(actorUserId, 100)) {
            return new AiChatResponse("您有一个正在进行的请求，请等待完成后再试。", new HashMap<>(), (String) null);
        }

        ToolAuditLogger.resetTranscript();
        String requestId = UUID.randomUUID().toString();

        Span agentSpan = tracer.spanBuilder("agent-chat")
                .setAttribute("user.id", actorUserId)
                .setAttribute("query.length", query.length())
                .startSpan();

        // 构建上下文
        String noteContext;
        try {
            Span ctxSpan = tracer.spanBuilder("context-assembly").startSpan();
            try {
                noteContext = contextAssembler.assemble(query, noteIds, actorUserId);
                ctxSpan.setAttribute("context.length", noteContext.length());
            } finally {
                ctxSpan.end();
            }
            log.info("Context assembled, length: {}", noteContext.length());
        } catch (Exception e) {
            log.error("Error building context: {}", e.getMessage(), e);
            agentSpan.setStatus(StatusCode.ERROR, "context assembly failed");
            agentSpan.end();
            ToolAuditLogger.resetTranscript();
            concurrencyGuard.release(actorUserId);
            return new AiChatResponse("抱歉，构建上下文时出错：" + e.getMessage(), new HashMap<>(), (String) null);
        }
        GuardrailResult contextCheck = inputGuardrail.scanUntrustedContent(noteContext);
        if (!contextCheck.passed()) {
            agentSpan.setStatus(StatusCode.ERROR, "untrusted context blocked");
            agentSpan.end();
            ToolAuditLogger.resetTranscript();
            concurrencyGuard.release(actorUserId);
            return new AiChatResponse(contextCheck.reason(), new HashMap<>(), (String) null);
        }

        // 时间上下文
        String currentTime = LocalDateTime.now().format(TIME_FORMATTER);
        String dayOfWeek = LocalDateTime.now().getDayOfWeek()
                .getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINESE);
        String timeContext = currentTime + "（" + dayOfWeek + "）";

        try {
            // 记录调用前的消息数量（用于 ToolCallAuditor）
            int preCallMessageCount = reliableChatMemoryStore.getMessages(memoryId).size();

            AgentInvocationResult invocation;
            Future<AgentInvocationResult> invocationFuture = null;
            CancellationToken cancelToken = null;
            String rawResponse;
            List<ToolAuditLogger.ToolAuditEntry> transcript = List.of();
            try {
                final String currentUserId = actorUserId;
                // 获取或创建取消令牌
                cancelToken = getOrCreateCancelToken(currentUserId, requestId);

                CancellationToken activeCancelToken = cancelToken;
                invocationFuture = securityExecutor.submit(
                        () -> invokeAgentWithContext(memoryId, actorUserId, query, timeContext, noteContext, activeCancelToken));
                invocation = invocationFuture.get(agentTimeoutSeconds, TimeUnit.SECONDS);
                rawResponse = invocation.rawResponse();
                transcript = invocation.transcript();
            } catch (TimeoutException e) {
                log.warn("Agent chat timed out after {}s for query: {}", agentTimeoutSeconds, query);
                cancelInvocation(invocationFuture, cancelToken);
                return new AiChatResponse("抱歉，处理时间过长，请稍后重试。", new HashMap<>(), (String) null);
            } catch (Exception e) {
                // 检查是否是工具调用次数超限（Agent 循环保护）
                String errMsg = extractRootMessage(e);
                if (isToolCallLimitError(e, errMsg)) {
                    log.warn("Agent exceeded max tool invocations for query: {}", query);
                    String lastToolResult = extractLastToolResult(memoryId, preCallMessageCount);
                    if (lastToolResult != null && !lastToolResult.isBlank()) {
                        return new AiChatResponse(lastToolResult, new HashMap<>(), (String) null);
                    }
                    return new AiChatResponse("操作已完成，请刷新查看最新结果。", new HashMap<>(), (String) null);
                }
                log.error("Error in agent invocation future: {}", e.getMessage(), e);
                return new AiChatResponse("抱歉，处理请求时出错：" + e.getMessage(), new HashMap<>(), (String) null);
            }

            // ToolCallAuditor 后置验证
            rawResponse = toolCallAuditor.validate(rawResponse, memoryId, reliableChatMemoryStore, preCallMessageCount);

            // 输出安全护栏
            rawResponse = outputGuardrail.sanitize(rawResponse, actorUserId);

            // 解析 PENDING_ACTION
            List<Map<String, Object>> pendingActions = collectPendingActions(rawResponse, transcript);
            String cleanResponse = removePendingActionMarkers(rawResponse);

            String actionJson = null;
            if (!pendingActions.isEmpty()) {
                try {
                    actionJson = objectMapper.writeValueAsString(pendingActions);
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize pending actions", e);
                }
            }

            Map<Integer, AiChatResponse.NoteSource> sources = buildNoteSources(noteIds, actorUserId);

            log.info("=== Agent Chat Response === Content length: {}", cleanResponse.length());

            // 异步提取语义记忆（仅对非简单操作类查询）
            if (shouldExtractMemory(query)) {
                memoryExtractionService.extractSemanticMemoryAsync(actorUserId, query, cleanResponse);
            }

            agentSpan.setAttribute("response.length", cleanResponse.length());
            agentSpan.setAttribute("pending_actions.count", pendingActions.size());

            String completeContent = removeInteractiveCardMarkers(cleanResponse);
            AiChatResponse response = new AiChatResponse(completeContent, sources, actionJson);
            // 附加工具执行审计记录（后续可通过配置控制是否返回前端）
            if (!transcript.isEmpty()) {
                response.setTranscript(transcript);
                log.info("Agent chat completed with {} tool executions, tokens used: {}",
                        transcript.size(), tokenBudget.getCurrentTokens());
            }
            if (!invocation.isMemoryPersisted()) {
                response.setDegraded(true);
                response.setDegradationReason("memory persistence failed; response is valid but chat memory may be incomplete");
                log.warn("Memory flush failed for actorUserId={}: {}",
                        actorUserId, invocation.memoryFlushError());
            }
            return response;

        } catch (Exception e) {
            log.error("Agent chat failed", e);
            agentSpan.setStatus(StatusCode.ERROR, e.getMessage());
            return new AiChatResponse("抱歉，处理请求时出错：" + e.getMessage(), new HashMap<>(), (String) null);
        } finally {
            ToolAuditLogger.resetTranscript();
            removeCancelToken(actorUserId, requestId);
            concurrencyGuard.release(actorUserId);
            agentSpan.end();
        }
    }

    /**
     * 调用 Agent。Retry 由 ResilientChatModel 在 LLM 调用层处理，此处不再重试。
     */
    private void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private String invokeAgent(String userId, String query, String timeContext, String noteContext) {
        return agentAssistant.chat(userId, query, timeContext, noteContext);
    }

    private AgentInvocationResult invokeAgentWithContext(
            String memoryId,
            String actorUserId,
            String query,
            String timeContext,
            String noteContext,
            CancellationToken cancelToken
    ) {
        resetAgentExecutionState();
        ToolAuditLogger.resetTranscript();
        AgentTraceListener.setCurrentUserId(actorUserId);
        com.ainote.app.config.AgentConfig.TOOL_CALL_COUNTER.get().set(0);
        ReliableChatMemoryStore.IN_AGENT_LOOP.set(true);
        ToolExecutionPipeline.setCancelToken(cancelToken);
        DeferredMemoryState.begin();
        try {
            String rawResponse = invokeAgent(memoryId, query, timeContext, noteContext);
            AgentInvocationResult result = new AgentInvocationResult(
                    rawResponse, List.copyOf(ToolAuditLogger.getTranscript()));

            try {
                reliableChatMemoryStore.flushDeferredWrites();
                result.setMemoryPersisted(true);
            } catch (Exception e) {
                log.error("Failed to flush deferred memory writes: {}", e.getMessage(), e);
                result.setMemoryPersisted(false);
                result.setMemoryFlushError(e.getMessage());
            }

            return result;
        } finally {
            DeferredMemoryState.clear();
            ToolExecutionPipeline.clearCancelToken();
            ReliableChatMemoryStore.IN_AGENT_LOOP.remove();
            AgentTraceListener.clearCurrentUserId();
            com.ainote.app.config.AgentConfig.TOOL_CALL_COUNTER.remove();
            resetAgentExecutionState();
            ToolAuditLogger.resetTranscript();
        }
    }

    private void resetAgentExecutionState() {
        toolLoopDetector.reset();
        tokenBudget.reset();
        GracefulDegradation.reset();
    }

    private void cancelInvocation(Future<?> invocationFuture, CancellationToken cancelToken) {
        if (cancelToken != null) {
            cancelToken.cancel();
        }
        if (invocationFuture != null) {
            invocationFuture.cancel(true);
        }
    }

    private List<Map<String, Object>> extractPendingActions(String response) {
        return pendingActionRegistry.extractActions(response);
    }

    private List<Map<String, Object>> collectPendingActions(
            String response,
            List<ToolAuditLogger.ToolAuditEntry> transcript
    ) {
        List<Map<String, Object>> actions = new ArrayList<>(extractPendingActions(response));
        if (transcript == null || transcript.isEmpty()) {
            return actions;
        }

        for (ToolAuditLogger.ToolAuditEntry entry : transcript) {
            if (entry.result() == null || !entry.result().contains("PENDING_ACTION:")) {
                continue;
            }
            for (Map<String, Object> action : extractPendingActions(entry.result())) {
                if (!actions.contains(action)) {
                    actions.add(action);
                }
            }
        }
        return actions;
    }

    private String removePendingActionMarkers(String response) {
        return pendingActionRegistry.removeMarkers(response);
    }

    private Map<Integer, AiChatResponse.NoteSource> buildNoteSources(List<String> noteIds, String userId) {
        Map<Integer, AiChatResponse.NoteSource> sources = new HashMap<>();
        if (noteIds == null) return sources;

        int index = 1;
        for (String noteId : noteIds) {
            Optional<com.ainote.app.entity.Note> noteOpt =
                    noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId);
            if (noteOpt.isPresent()) {
                com.ainote.app.entity.Note note = noteOpt.get();
                sources.put(index, new AiChatResponse.NoteSource(note.getId(), note.getTitle()));
                index++;
            }
        }
        return sources;
    }

    /**
     * 流式对话回调接口
     */
    public interface StreamCallback {
        void onToken(String token);
        void onComplete(AiChatResponse response);
        void onError(String error);
        void onProgress(String step, String detail);
    }

    /**
     * 流式 Agent 对话
     */
    public void chatStream(String query, List<String> noteIds, String userId, StreamCallback callback) {
        chatStream(query, noteIds, userId, UUID.randomUUID().toString(), callback);
    }

    public void chatStream(String query, List<String> noteIds, String userId, String requestId, StreamCallback callback) {
        doChatStream(query, noteIds, userId, userId, requestId, GuardrailMode.USER_INPUT, callback);
    }

    public void chatStreamAlreadyChecked(String query, List<String> noteIds, String userId, StreamCallback callback) {
        chatStreamAlreadyChecked(query, noteIds, userId, UUID.randomUUID().toString(), callback);
    }

    public void chatStreamAlreadyChecked(String query, List<String> noteIds, String userId, String requestId, StreamCallback callback) {
        doChatStream(query, noteIds, userId, userId, requestId, GuardrailMode.ALREADY_CHECKED_USER_INPUT, callback);
    }

    private void doChatStream(
            String query,
            List<String> noteIds,
            String memoryId,
            String actorUserId,
            String requestId,
            GuardrailMode guardrailMode,
            StreamCallback callback
    ) {
        log.info("=== Agent Chat Stream Request === Query: {}, NoteIds: {}", query, noteIds);

        // 输入安全护栏
        if (guardrailMode == GuardrailMode.USER_INPUT) {
            GuardrailResult inputCheck = inputGuardrail.check(query);
            if (!inputCheck.passed()) {
                callback.onError(inputCheck.reason());
                return;
            }
        }

        AiChatResponse directPendingAction = tryBuildSelectedNoteDeleteConfirmation(query, noteIds, actorUserId);
        if (directPendingAction != null) {
            String content = directPendingAction.getContent();
            for (int i = 0; i < content.length(); i++) {
                callback.onToken(String.valueOf(content.charAt(i)));
            }
            callback.onComplete(directPendingAction);
            return;
        }

        // 并发控制
        if (!concurrencyGuard.tryAcquire(actorUserId, 100)) {
            callback.onError("您有一个正在进行的请求，请等待完成后再试。");
            return;
        }

        ToolAuditLogger.resetTranscript();
        int preCallMessageCount = 0;

        try {
            callback.onProgress("thinking", "正在分析您的请求...");

            callback.onProgress("searching", "正在检索笔记上下文...");
            String noteContext = contextAssembler.assemble(query, noteIds, actorUserId);
            GuardrailResult contextCheck = inputGuardrail.scanUntrustedContent(noteContext);
            if (!contextCheck.passed()) {
                callback.onError(contextCheck.reason());
                return;
            }

            String currentTime = LocalDateTime.now().format(TIME_FORMATTER);
            String dayOfWeek = LocalDateTime.now().getDayOfWeek()
                    .getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINESE);
            String timeContext = currentTime + "（" + dayOfWeek + "）";

            callback.onProgress("calling_agent", "正在推理和执行...");

            // 记录调用前的消息数量
            preCallMessageCount = reliableChatMemoryStore.getMessages(memoryId).size();

            AgentInvocationResult invocation;
            Future<AgentInvocationResult> invocationFuture = null;
            CancellationToken cancelToken = null;
            String rawResponse;
            List<ToolAuditLogger.ToolAuditEntry> transcript = List.of();
            try {
                AgentTraceListener.registerProgressCallback(actorUserId, callback::onProgress);

                // 获取或创建取消令牌
                cancelToken = getOrCreateCancelToken(actorUserId, requestId);

                CancellationToken activeCancelToken = cancelToken;
                invocationFuture = securityExecutor.submit(
                        () -> invokeAgentWithContext(memoryId, actorUserId, query, timeContext, noteContext, activeCancelToken));
                invocation = invocationFuture.get(agentTimeoutSeconds, TimeUnit.SECONDS);
                rawResponse = invocation.rawResponse();
                transcript = invocation.transcript();
            } catch (TimeoutException e) {
                log.warn("Agent chat stream timed out after {}s", agentTimeoutSeconds);
                cancelInvocation(invocationFuture, cancelToken);
                callback.onError("处理时间过长，请稍后重试。");
                return;
            } finally {
                AgentTraceListener.unregisterProgressCallback(actorUserId);
            }

            // ToolCallAuditor 后置验证
            rawResponse = toolCallAuditor.validate(rawResponse, memoryId, reliableChatMemoryStore, preCallMessageCount);

            // 输出安全护栏
            rawResponse = outputGuardrail.sanitize(rawResponse, actorUserId);

            callback.onProgress("generating", "正在生成回复...");

            List<Map<String, Object>> pendingActions = collectPendingActions(rawResponse, transcript);
            String cleanResponse = removePendingActionMarkers(rawResponse);

            String actionJson = null;
            if (!pendingActions.isEmpty()) {
                try {
                    actionJson = objectMapper.writeValueAsString(pendingActions);
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize pending actions", e);
                }
            }

            Map<Integer, AiChatResponse.NoteSource> sources = buildNoteSources(noteIds, actorUserId);

            String streamContent = removeInteractiveCardMarkers(cleanResponse);
            for (int i = 0; i < streamContent.length(); i++) {
                callback.onToken(String.valueOf(streamContent.charAt(i)));
            }

            String completeContent = removeInteractiveCardMarkers(cleanResponse);
            AiChatResponse response = new AiChatResponse(completeContent, sources, actionJson);
            // 附加工具执行审计记录
            if (!transcript.isEmpty()) {
                response.setTranscript(transcript);
            }
            if (!invocation.isMemoryPersisted()) {
                response.setDegraded(true);
                response.setDegradationReason("memory persistence failed; response is valid but chat memory may be incomplete");
                log.warn("Memory flush failed for actorUserId={}: {}",
                        actorUserId, invocation.memoryFlushError());
            }
            callback.onComplete(response);

            // 异步提取语义记忆（仅对非简单操作类查询）
            if (shouldExtractMemory(query)) {
                memoryExtractionService.extractSemanticMemoryAsync(actorUserId, query, cleanResponse);
            }

        } catch (Exception e) {
            // 流式也处理工具调用超限
            String errMsg = extractRootMessage(e);
            if (isToolCallLimitError(e, errMsg)) {
                log.warn("Agent stream exceeded max tool invocations for query: {}", query);
                String lastToolResult = extractLastToolResult(memoryId, preCallMessageCount);
                String resp = (lastToolResult != null && !lastToolResult.isBlank())
                        ? lastToolResult
                        : "操作已完成，请刷新查看最新结果。";
                for (int i = 0; i < resp.length(); i++) {
                    callback.onToken(String.valueOf(resp.charAt(i)));
                }
                callback.onComplete(new AiChatResponse(resp, new HashMap<>(), (String) null));
                return;
            }
            log.error("Agent chat stream failed", e);
            callback.onError("处理请求时出错：" + e.getMessage());
        } finally {
            ToolAuditLogger.resetTranscript();
            removeCancelToken(actorUserId, requestId);
            concurrencyGuard.release(actorUserId);
        }
    }

    /**
     * HITL 反馈：用户确认或拒绝 PENDING_ACTION 后，将结果回传给 Agent 对话
     * 使 Agent 知晓用户的决策，可以继续调整策略
     *
     * @param userId    用户 ID
     * @param actionJson 原始 PENDING_ACTION 的 JSON 内容
     * @param confirmed  用户是否确认
     * @param feedback   用户附加的反馈文本（可选）
     * @return Agent 的后续回复
     */
    public AiChatResponse confirmAction(String userId, String actionJson, boolean confirmed, String feedback) {
        AiChatResponse directResult = tryHandlePendingActionDirectly(actionJson, confirmed);
        if (directResult != null) {
            return directResult;
        }

        String feedbackMessage;
        if (confirmed) {
            feedbackMessage = "用户已确认执行操作。操作详情: " + actionJson;
            if (feedback != null && !feedback.isBlank()) {
                feedbackMessage += "\n用户补充: " + feedback;
            }
            feedbackMessage += "\n请简要确认操作已完成。";
        } else {
            feedbackMessage = "用户拒绝了操作。原操作: " + actionJson;
            if (feedback != null && !feedback.isBlank()) {
                feedbackMessage += "\n用户说明: " + feedback;
            }
            feedbackMessage += "\n请理解用户的顾虑，建议替代方案或询问用户的意图。";
        }

        return chat(feedbackMessage, List.of(), userId);
    }

    /**
     * 当前已选中一篇笔记，且用户只说“删除笔记/删除当前笔记/删除这篇”时，
     * 操作对象应由前端传入的 noteIds 决定，而不是交给 LLM 从历史或 RAG 结果中猜。
     */
    private AiChatResponse tryBuildSelectedNoteDeleteConfirmation(String query, List<String> noteIds, String userId) {
        if (noteIds == null || noteIds.size() != 1) {
            return null;
        }
        if (!isCurrentSelectedNoteDeleteRequest(query)) {
            return null;
        }

        String noteId = noteIds.get(0);
        Optional<com.ainote.app.entity.Note> noteOpt =
                noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId);
        if (noteOpt.isEmpty()) {
            return null;
        }

        com.ainote.app.entity.Note note = noteOpt.get();
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "DELETE_NOTE");
        action.put("noteId", note.getId());
        action.put("title", note.getTitle());

        try {
            String pendingActionJson = objectMapper.writeValueAsString(action);
            String content = String.format(
                    "需要你确认一下：\n\n⚠️ 确认删除「%s」吗？\n（删除后可以从回收站恢复）",
                    note.getTitle()
            );
            return new AiChatResponse(content, buildNoteSources(noteIds, userId), pendingActionJson);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize deterministic pending action", e);
            return null;
        }
    }

    private boolean isCurrentSelectedNoteDeleteRequest(String query) {
        if (query == null) return false;
        String q = query.trim().replaceAll("\\s+", "");
        if (q.isBlank()) return false;
        if (q.contains("永久删除") || q.contains("清空")) {
            return false;
        }

        boolean hasDeleteVerb = q.contains("删除") || q.contains("删掉") || q.contains("移除");
        if (!hasDeleteVerb) {
            return false;
        }

        boolean explicitlyCurrent = q.contains("当前") || q.contains("选中")
                || q.contains("这篇") || q.contains("这条") || q.contains("这则");
        boolean genericNoteDelete = q.matches("^(请|帮我|麻烦|麻烦你)?(删除|删掉|移除)(当前|选中|这篇|这条|这则)?笔记[。.!！?？]*$");
        return genericNoteDelete || (explicitlyCurrent && !hasComplexTaskConnector(q));
    }

    private boolean hasComplexTaskConnector(String q) {
        return q.contains("然后")
                || q.contains("再")
                || q.contains("同时")
                || q.contains("顺便")
                || q.contains("之后")
                || q.contains("以及")
                || q.contains("并且")
                || q.contains("并")
                || q.contains(";")
                || q.contains("；");
    }

    private AiChatResponse tryHandlePendingActionDirectly(String actionJson, boolean confirmed) {
        JsonNode action = parseFirstPendingAction(actionJson);
        if (action == null || !action.has("type")) {
            return null;
        }

        String type = action.path("type").asText("");
        if (!"DELETE_NOTE".equals(type)) {
            return null;
        }

        String noteId = action.path("noteId").asText("");
        String titleFromAction = action.path("title").asText("这篇笔记");
        if (noteId.isBlank()) {
            return new AiChatResponse("操作失败：缺少笔记编号。", new HashMap<>(), (String) null);
        }

        if (!confirmed) {
            return new AiChatResponse("好的，已取消删除「" + titleFromAction + "」。", new HashMap<>(), (String) null);
        }

        Optional<com.ainote.app.model.Note> noteOpt = noteService.getById(noteId);
        if (noteOpt.isEmpty()) {
            return new AiChatResponse("没有找到「" + titleFromAction + "」，它可能已经被删除或不属于当前用户。", new HashMap<>(), (String) null);
        }

        String title = noteOpt.get().getTitle();
        noteService.delete(noteId);
        return new AiChatResponse("已删除「" + title + "」，并移入回收站。", new HashMap<>(), (String) null);
    }

    private JsonNode parseFirstPendingAction(String actionJson) {
        return pendingActionRegistry.parseFirstSubmittedAction(actionJson).orElse(null);
    }

    /**
     * 判断是否需要提取语义记忆
     * 短问候（CHAT）不包含值得记住的信息，跳过以节省API调用
     * 其他所有查询（STANDARD）都可能包含用户偏好/事实
     */
    private boolean shouldExtractMemory(String query) {
        if (query == null || query.isBlank()) return false;
        ContextAssembler.Intent intent = contextAssembler.detectIntent(query);
        return intent != ContextAssembler.Intent.CHAT;
    }

    /**
     * 检查异常是否是工具调用次数超限（LangChain4j maxSequentialToolsInvocations 抛出）
     */
    private boolean isToolCallLimitError(Exception e, String errMsg) {
        Throwable cause = e;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null) {
                if (msg.contains("sequential tool invocations") || msg.contains("工具调用上限")) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * 提取异常链中的根因消息
     */
    private String extractRootMessage(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause.getMessage() != null) {
                return cause.getMessage();
            }
            cause = cause.getCause();
        }
        return null;
    }

    /**
     * 从本轮新增 chat memory 中提取最后一条成功的工具执行结果。
     * 用于 Agent 超限时返回已完成的当前轮工具结果，避免串到历史轮次。
     */
    private String extractLastToolResult(String memoryId, int preCallMessageCount) {
        try {
            List<ChatMessage> messages = reliableChatMemoryStore.getMessages(memoryId);
            int start = Math.max(0, Math.min(preCallMessageCount, messages.size()));
            for (int i = messages.size() - 1; i >= start; i--) {
                ChatMessage message = messages.get(i);
                if (message instanceof ToolExecutionResultMessage toolMessage) {
                    String result = toolMessage.text();
                    if (isSuccessfulToolResult(result)) {
                        return result.trim();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract last tool result: {}", e.getMessage());
        }
        return null;
    }

    private boolean isSuccessfulToolResult(String result) {
        return result != null && !result.isBlank()
                && !result.contains("操作失败") && !result.contains("未找到")
                && !result.contains("缺少") && !result.contains("检测到重复调用");
    }

    private String removeInteractiveCardMarkers(String content) {
        if (content == null) {
            return null;
        }
        return content.replaceAll("INTERACTIVE_CARD:\\{[^\\n]*\\}\\n?", "").trim();
    }
}
