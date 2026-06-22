package com.ainote.ai.service;

import com.ainote.ai.agent.AgentAssistant;
import com.ainote.ai.agent.CancellationToken;
import com.ainote.ai.agent.ConcurrencyGuard;
import com.ainote.ai.agent.budget.TokenBudget;
import com.ainote.ai.agent.guardrail.GuardrailResult;
import com.ainote.ai.agent.guardrail.InputGuardrail;
import com.ainote.ai.agent.guardrail.OutputGuardrail;
import com.ainote.ai.agent.pending.PendingActionRegistry;
import com.ainote.ai.agent.pipeline.GracefulDegradation;
import com.ainote.ai.agent.pipeline.ToolAuditLogger;
import com.ainote.ai.agent.pipeline.ToolExecutionPipeline;
import com.ainote.ai.agent.tools.ToolLoopDetector;
import com.ainote.ai.config.AgentConfig;
import com.ainote.ai.entity.UserMemory;
import com.ainote.ai.memory.ReliableChatMemoryStore;
import com.ainote.ai.model.AiChatResponse;
import com.ainote.ai.repository.UserMemoryRepository;
import com.ainote.common.security.UserContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AgentAssistant agentAssistant;
    private final UserMemoryRepository userMemoryRepository;
    private final ObjectMapper objectMapper;
    private final ExecutorService securityExecutor;
    private final ContextAssembler contextAssembler;
    private final ReliableChatMemoryStore reliableChatMemoryStore;
    private final ToolLoopDetector toolLoopDetector;
    private final ConcurrencyGuard concurrencyGuard;
    private final TokenBudget tokenBudget;
    private final InputGuardrail inputGuardrail;
    private final OutputGuardrail outputGuardrail;
    private final PendingActionRegistry pendingActionRegistry;

    /** 用户 → 取消令牌的注册表 */
    private final ConcurrentHashMap<String, CancellationToken> cancelTokenRegistry = new ConcurrentHashMap<>();

    @Value("${app.agent.timeout-seconds:300}")
    private int agentTimeoutSeconds;

    public AgentService(
            AgentAssistant agentAssistant,
            UserMemoryRepository userMemoryRepository,
            @Qualifier("securityExecutor") ExecutorService securityExecutor,
            ContextAssembler contextAssembler,
            ReliableChatMemoryStore reliableChatMemoryStore,
            ToolLoopDetector toolLoopDetector,
            ConcurrencyGuard concurrencyGuard,
            TokenBudget tokenBudget,
            InputGuardrail inputGuardrail,
            OutputGuardrail outputGuardrail,
            ObjectMapper objectMapper,
            PendingActionRegistry pendingActionRegistry
    ) {
        this.agentAssistant = agentAssistant;
        this.userMemoryRepository = userMemoryRepository;
        this.objectMapper = objectMapper;
        this.securityExecutor = securityExecutor;
        this.contextAssembler = contextAssembler;
        this.reliableChatMemoryStore = reliableChatMemoryStore;
        this.toolLoopDetector = toolLoopDetector;
        this.concurrencyGuard = concurrencyGuard;
        this.tokenBudget = tokenBudget;
        this.inputGuardrail = inputGuardrail;
        this.outputGuardrail = outputGuardrail;
        this.pendingActionRegistry = pendingActionRegistry;
    }

    /**
     * 创建取消令牌并注册。由 AiController 在 SSE 建立时调用。
     */
    public CancellationToken createCancelToken(String userId) {
        CancellationToken token = new CancellationToken();
        cancelTokenRegistry.put(userId, token);
        return token;
    }

    private void removeCancelToken(String userId) {
        cancelTokenRegistry.remove(userId);
    }

    public void clearMemory(String userId) {
        log.info("Clearing memory for user: {}", userId);
        userMemoryRepository.deleteByUserId(userId);
    }

    public AiChatResponse chat(String query, List<String> noteIds) {
        Long userIdLong = UserContext.getCurrentUserId();
        String userId = userIdLong != null ? userIdLong.toString() : "anonymous";
        return chat(query, noteIds, userId);
    }

    public AiChatResponse chat(String query, List<String> noteIds, String userId) {
        log.info("=== Agent Chat Request === Query: {}, NoteIds: {}", query, noteIds);

        // 输入安全护栏
        GuardrailResult inputCheck = inputGuardrail.check(query);
        if (!inputCheck.passed()) {
            return new AiChatResponse(inputCheck.reason(), new HashMap<>(), (String) null);
        }

        // 并发控制
        if (!concurrencyGuard.tryAcquire(userId, 100)) {
            return new AiChatResponse("您有一个正在进行的请求，请等待完成后再试。", new HashMap<>(), (String) null);
        }

        toolLoopDetector.reset();
        ToolAuditLogger.resetTranscript();
        tokenBudget.reset();
        GracefulDegradation.reset();

        try {
            // Build context
            String noteContext;
            try {
                noteContext = contextAssembler.assemble(query, noteIds, userId);
                log.info("Context assembled, length: {}", noteContext.length());
            } catch (Exception e) {
                log.error("Error building context: {}", e.getMessage(), e);
                return new AiChatResponse("抱歉，构建上下文时出错：" + e.getMessage(), new HashMap<>(), (String) null);
            }

            // Time context
            String currentTime = LocalDateTime.now().format(TIME_FORMATTER);
            String dayOfWeek = LocalDateTime.now().getDayOfWeek()
                    .getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINESE);
            String timeContext = currentTime + "（" + dayOfWeek + "）";

            String rawResponse;
            try {
                CancellationToken cancelToken = cancelTokenRegistry.computeIfAbsent(
                        userId, k -> new CancellationToken());

                rawResponse = CompletableFuture.supplyAsync(() -> {
                    AgentConfig.TOOL_CALL_COUNTER.get().set(0);

                    ToolExecutionPipeline.setCancelToken(cancelToken);
                    try {
                        return invokeAgent(userId, query, timeContext, noteContext);
                    } finally {
                        ToolExecutionPipeline.clearCancelToken();

                        AgentConfig.TOOL_CALL_COUNTER.remove();
                    }
                }, securityExecutor).get(agentTimeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.warn("Agent chat timed out after {}s", agentTimeoutSeconds);
                return new AiChatResponse("抱歉，处理时间过长，请稍后重试。", new HashMap<>(), (String) null);
            } catch (Exception e) {
                String errMsg = extractRootMessage(e);
                if (isToolCallLimitError(e, errMsg)) {
                    log.warn("Agent exceeded max tool invocations");
                    String lastToolResult = extractLastToolResult(userId);
                    if (lastToolResult != null && !lastToolResult.isBlank()) {
                        return new AiChatResponse(lastToolResult, new HashMap<>(), (String) null);
                    }
                    return new AiChatResponse("操作已完成，请刷新查看最新结果。", new HashMap<>(), (String) null);
                }
                log.error("Error in agent call: {}", e.getMessage(), e);
                return new AiChatResponse("抱歉，处理请求时出错：" + e.getMessage(), new HashMap<>(), (String) null);
            }

            // 输出安全护栏
            rawResponse = outputGuardrail.sanitize(rawResponse, userId);

            // Parse PENDING_ACTION
            List<Map<String, Object>> pendingActions = extractPendingActions(rawResponse);
            String cleanResponse = removePendingActionMarkers(rawResponse);

            String actionJson = null;
            if (!pendingActions.isEmpty()) {
                try {
                    actionJson = objectMapper.writeValueAsString(pendingActions);
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize pending actions", e);
                }
            }

            log.info("=== Agent Chat Response === Content length: {}", cleanResponse.length());

            AiChatResponse response = new AiChatResponse(cleanResponse, new HashMap<>(), actionJson);
            // 附加工具执行审计记录
            List<ToolAuditLogger.ToolAuditEntry> transcript = ToolAuditLogger.getTranscript();
            if (!transcript.isEmpty()) {
                response.setTranscript(transcript);
                log.info("Agent chat completed with {} tool executions, tokens used: {}",
                        transcript.size(), tokenBudget.getCurrentTokens());
            }
            return response;

        } catch (Exception e) {
            log.error("Agent chat failed", e);
            return new AiChatResponse("抱歉，处理请求时出错：" + e.getMessage(), new HashMap<>(), (String) null);
        } finally {
            toolLoopDetector.reset();
            ToolAuditLogger.resetTranscript();
            tokenBudget.reset();
            GracefulDegradation.reset();
            removeCancelToken(userId);
            concurrencyGuard.release(userId);
        }
    }

    /**
     * Stream callback interface
     */
    public interface StreamCallback {
        void onToken(String token);
        void onComplete(AiChatResponse response);
        void onError(String error);
        void onProgress(String step, String detail);
    }

    /**
     * Streaming agent chat
     */
    public void chatStream(String query, List<String> noteIds, String userId, StreamCallback callback) {
        log.info("=== Agent Chat Stream Request === Query: {}, NoteIds: {}", query, noteIds);

        // 输入安全护栏
        GuardrailResult inputCheck = inputGuardrail.check(query);
        if (!inputCheck.passed()) {
            callback.onError(inputCheck.reason());
            return;
        }

        // 并发控制
        if (!concurrencyGuard.tryAcquire(userId, 100)) {
            callback.onError("您有一个正在进行的请求，请等待完成后再试。");
            return;
        }

        toolLoopDetector.reset();
        ToolAuditLogger.resetTranscript();
        tokenBudget.reset();
        GracefulDegradation.reset();

        try {
            callback.onProgress("thinking", "正在分析您的请求...");

            callback.onProgress("searching", "正在检索笔记上下文...");
            String noteContext = contextAssembler.assemble(query, noteIds, userId);

            String currentTime = LocalDateTime.now().format(TIME_FORMATTER);
            String dayOfWeek = LocalDateTime.now().getDayOfWeek()
                    .getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINESE);
            String timeContext = currentTime + "（" + dayOfWeek + "）";

            callback.onProgress("calling_agent", "正在推理和执行...");

            String rawResponse;
            try {
                CancellationToken cancelToken = cancelTokenRegistry.computeIfAbsent(
                        userId, k -> new CancellationToken());

                rawResponse = CompletableFuture.supplyAsync(() -> {
                    AgentConfig.TOOL_CALL_COUNTER.get().set(0);

                    ToolExecutionPipeline.setCancelToken(cancelToken);
                    try {
                        return invokeAgent(userId, query, timeContext, noteContext);
                    } finally {
                        ToolExecutionPipeline.clearCancelToken();

                        AgentConfig.TOOL_CALL_COUNTER.remove();
                    }
                }, securityExecutor).get(agentTimeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                callback.onError("处理时间过长，请稍后重试。");
                return;
            }

            // 输出安全护栏
            rawResponse = outputGuardrail.sanitize(rawResponse, userId);

            callback.onProgress("generating", "正在生成回复...");

            List<Map<String, Object>> pendingActions = extractPendingActions(rawResponse);
            String cleanResponse = removePendingActionMarkers(rawResponse);

            String actionJson = null;
            if (!pendingActions.isEmpty()) {
                try {
                    actionJson = objectMapper.writeValueAsString(pendingActions);
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize pending actions", e);
                }
            }

            // Simulate streaming by sending char by char
            for (int i = 0; i < cleanResponse.length(); i++) {
                callback.onToken(String.valueOf(cleanResponse.charAt(i)));
            }

            AiChatResponse response = new AiChatResponse(cleanResponse, new HashMap<>(), actionJson);
            List<ToolAuditLogger.ToolAuditEntry> transcript = ToolAuditLogger.getTranscript();
            if (!transcript.isEmpty()) {
                response.setTranscript(transcript);
            }
            callback.onComplete(response);

        } catch (Exception e) {
            String errMsg = extractRootMessage(e);
            if (isToolCallLimitError(e, errMsg)) {
                String lastToolResult = extractLastToolResult(userId);
                String resp = (lastToolResult != null && !lastToolResult.isBlank())
                        ? lastToolResult : "操作已完成，请刷新查看最新结果。";
                for (int i = 0; i < resp.length(); i++) {
                    callback.onToken(String.valueOf(resp.charAt(i)));
                }
                callback.onComplete(new AiChatResponse(resp, new HashMap<>(), (String) null));
                return;
            }
            log.error("Agent chat stream failed", e);
            callback.onError("处理请求时出错：" + e.getMessage());
        } finally {
            toolLoopDetector.reset();
            ToolAuditLogger.resetTranscript();
            tokenBudget.reset();
            GracefulDegradation.reset();
            removeCancelToken(userId);
            concurrencyGuard.release(userId);
        }
    }

    /**
     * 调用 Agent。Retry 由 ResilientChatModel 在 LLM 调用层处理。
     */
    private String invokeAgent(String userId, String query, String timeContext, String noteContext) {
        return agentAssistant.chat(userId, query, timeContext, noteContext);
    }

    private List<Map<String, Object>> extractPendingActions(String response) {
        return pendingActionRegistry.extractActions(response);
    }

    private String removePendingActionMarkers(String response) {
        return pendingActionRegistry.removeMarkers(response);
    }

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

    private String extractRootMessage(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause.getMessage() != null) return cause.getMessage();
            cause = cause.getCause();
        }
        return null;
    }

    private String extractLastToolResult(String userId) {
        try {
            List<UserMemory> memories = userMemoryRepository.findAllByUserIdOrderByCreatedAtAsc(userId);
            for (int i = memories.size() - 1; i >= 0; i--) {
                UserMemory mem = memories.get(i);
                if ("TOOL_EXECUTION_RESULT".equals(mem.getMessageType())) {
                    String result = mem.getToolResult();
                    if (result != null && !result.isBlank()
                            && !result.contains("操作失败") && !result.contains("未找到")
                            && !result.contains("缺少") && !result.contains("检测到重复调用")) {
                        return result.trim();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract last tool result: {}", e.getMessage());
        }
        return null;
    }
}
