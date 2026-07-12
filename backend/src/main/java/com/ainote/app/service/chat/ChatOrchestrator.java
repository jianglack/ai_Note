package com.ainote.app.service.chat;

import com.ainote.app.agent.guardrail.GuardrailResult;
import com.ainote.app.agent.guardrail.InputGuardrail;
import com.ainote.app.model.AiChatResponse;
import com.ainote.app.model.memory.MemoryForgetResponse;
import com.ainote.app.service.MemoryControlService;
import com.ainote.app.service.MemoryPrivacyService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);

    private final ChatStrategy agentStrategy;
    private final ChatStrategy fallbackStrategy;
    private final ReadOnlyStreamingChatService readOnlyStreamingChatService;
    private final CircuitBreaker circuitBreaker;
    private final ChatMetrics chatMetrics;
    private final InputGuardrail inputGuardrail;
    private final MemoryPrivacyService memoryPrivacyService;
    private final MemoryControlService memoryControlService;

    public ChatOrchestrator(
            AgentChatStrategy agentStrategy,
            ReadOnlyFallbackChatService fallbackStrategy,
            ReadOnlyStreamingChatService readOnlyStreamingChatService,
            CircuitBreakerRegistry circuitBreakerRegistry,
            ChatMetrics chatMetrics,
            InputGuardrail inputGuardrail,
            MemoryPrivacyService memoryPrivacyService,
            MemoryControlService memoryControlService) {
        this.agentStrategy = agentStrategy;
        this.fallbackStrategy = fallbackStrategy;
        this.readOnlyStreamingChatService = readOnlyStreamingChatService;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("agent-chat");
        this.chatMetrics = chatMetrics;
        this.inputGuardrail = inputGuardrail;
        this.memoryPrivacyService = memoryPrivacyService;
        this.memoryControlService = memoryControlService;
    }

    public AiChatResponse chat(String query, List<String> noteIds, String userId) {
        chatMetrics.recordRequest();

        GuardrailResult guardResult = inputGuardrail.check(query);
        if (!guardResult.passed()) {
            return buildRejectionResponse(guardResult.reason());
        }

        Optional<AiChatResponse> governedMemoryResponse = handleGovernedMemoryCommand(query, userId);
        if (governedMemoryResponse.isPresent()) {
            return governedMemoryResponse.get();
        }

        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            log.info("CircuitBreaker OPEN, routing directly to fallback for user={}", userId);
            chatMetrics.recordCircuitOpen();
            return executeFallback(query, noteIds, userId, "服务暂时繁忙，已自动切换回复模式");
        }

        long startTime = System.currentTimeMillis();
        try {
            AiChatResponse response = circuitBreaker.executeSupplier(
                    () -> agentStrategy.chat(query, noteIds, userId));
            long latency = System.currentTimeMillis() - startTime;
            chatMetrics.recordSuccess("AGENT", latency);
            response.setChatMode("AGENT");
            response.setDegraded(false);
            return response;

        } catch (ChatStrategyException e) {
            long latency = System.currentTimeMillis() - startTime;
            log.warn("Agent returned error response (errorCode={}), falling back. latency={}ms",
                    e.getErrorCode(), latency, e);
            chatMetrics.recordFallback("AGENT_ERROR_RESPONSE", latency);
            return executeFallback(query, noteIds, userId, e.getMessage());

        } catch (CallNotPermittedException e) {
            log.warn("CircuitBreaker rejected call, falling back", e);
            chatMetrics.recordCircuitOpen();
            return executeFallback(query, noteIds, userId, "服务暂时繁忙，已自动切换回复模式");

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("Agent threw exception, falling back. latency={}ms", latency, e);
            chatMetrics.recordFallback("AGENT_EXCEPTION", latency);
            return executeFallback(query, noteIds, userId, "AI 助手暂时不可用，已切换简化回复");
        }
    }

    public void chatStream(String query, List<String> noteIds,
                           String userId, StreamCallback callback) {
        chatStream(query, noteIds, userId, java.util.UUID.randomUUID().toString(), callback);
    }

    public void chatStream(String query, List<String> noteIds,
                           String userId, String requestId, StreamCallback callback) {
        chatMetrics.recordRequest();

        GuardrailResult guardResult = inputGuardrail.check(query);
        if (!guardResult.passed()) {
            AiChatResponse rejection = buildRejectionResponse(guardResult.reason());
            callback.onToken(guardResult.reason());
            callback.onComplete(rejection);
            return;
        }

        Optional<AiChatResponse> governedMemoryResponse = handleGovernedMemoryCommand(query, userId);
        if (governedMemoryResponse.isPresent()) {
            AiChatResponse response = governedMemoryResponse.get();
            callback.onToken(response.getContent());
            callback.onComplete(response);
            return;
        }

        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            chatMetrics.recordCircuitOpen();
            fallbackStrategy.chatStream(query, noteIds, userId, callback);
            return;
        }

        if (readOnlyStreamingChatService.canHandle(query, noteIds)) {
            long startTime = System.currentTimeMillis();
            try {
                boolean handled = readOnlyStreamingChatService.chatStream(query, noteIds, userId, callback);
                long latency = System.currentTimeMillis() - startTime;
                if (handled) {
                    chatMetrics.recordSuccess("DIRECT_STREAM", latency);
                    return;
                }
                chatMetrics.recordFallback("DIRECT_STREAM_FAILURE", latency);
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - startTime;
                log.warn("Direct read-only stream failed, routing to agent. latency={}ms", latency, e);
                chatMetrics.recordFallback("DIRECT_STREAM_FAILURE", latency);
            }
        }

        TokenTrackingCallback trackingCallback = new TokenTrackingCallback(callback);
        long startTime = System.currentTimeMillis();

        try {
            circuitBreaker.executeRunnable(
                    () -> agentStrategy.chatStream(query, noteIds, userId, requestId, trackingCallback));
            long latency = System.currentTimeMillis() - startTime;
            chatMetrics.recordSuccess("AGENT_STREAM", latency);

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.warn("Agent stream failed, hasEmittedTokens={}, latency={}ms",
                    trackingCallback.hasEmittedTokens(), latency, e);
            chatMetrics.recordFallback("AGENT_STREAM_FAILURE", latency);

            if (!trackingCallback.isCompleted() && !trackingCallback.hasEmittedTokens()) {
                fallbackStrategy.chatStream(query, noteIds, userId, callback);
            } else if (!trackingCallback.isCompleted()) {
                AiChatResponse partial = new AiChatResponse(
                        trackingCallback.getEmittedContent(), new HashMap<>());
                partial.setDegraded(true);
                partial.setChatMode("AGENT_PARTIAL");
                partial.setDegradationReason("回复过程中出现中断，内容可能不完整");
                callback.onComplete(partial);
            }
        }
    }

    private AiChatResponse executeFallback(String query, List<String> noteIds,
                                           String userId, String reason) {
        try {
            AiChatResponse response = fallbackStrategy.chat(query, noteIds, userId);
            response.setDegradationReason(reason);
            return response;
        } catch (Exception e) {
            log.error("Fallback strategy also failed", e);
            chatMetrics.recordTotalFailure();
            return buildErrorResponse("抱歉，服务暂时不可用，请稍后再试");
        }
    }

    private AiChatResponse buildRejectionResponse(String message) {
        AiChatResponse response = new AiChatResponse(message, new HashMap<>());
        response.setChatMode("REJECTED");
        response.setDegraded(false);
        return response;
    }

    private Optional<AiChatResponse> handleGovernedMemoryCommand(String query, String userId) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        if (isSensitiveMemoryWriteRequest(query)) {
            AiChatResponse response = new AiChatResponse(
                    "我不能为你保存身份证号、手机号、密钥、邮箱等敏感个人信息；这类内容不会写入长期记忆。"
                            + "如果只是当前这轮对话需要处理，请尽量使用脱敏信息。",
                    new HashMap<>());
            response.setChatMode("MEMORY_GOVERNED");
            response.setDegraded(false);
            return Optional.of(response);
        }
        if (isForgetAllMemoryRequest(query)) {
            MemoryForgetResponse result = memoryControlService.forgetAllActiveMemories(
                    userId,
                    "natural language forget-all request");
            AiChatResponse response = new AiChatResponse(
                    "已删除你的长期记忆 " + result.deletedCount()
                            + " 条。说明：这只处理长期记忆，不等同于删除当前聊天记录、审计事件或合规保留日志。",
                    new HashMap<>());
            response.setChatMode("MEMORY_GOVERNED");
            response.setDegraded(false);
            return Optional.of(response);
        }
        if (isLongTermMemoryRecallRequest(query) && !hasActiveLongTermMemory(userId)) {
            AiChatResponse response = new AiChatResponse(
                    "我当前没有与你这个问题对应的可用长期记忆，因此不能假装知道你的偏好或项目背景。",
                    new HashMap<>());
            response.setChatMode("MEMORY_GOVERNED");
            response.setDegraded(false);
            return Optional.of(response);
        }
        return Optional.empty();
    }

    private boolean hasActiveLongTermMemory(String userId) {
        try {
            return memoryControlService.hasActiveMemories(userId);
        } catch (RuntimeException e) {
            log.warn("Failed to check active long-term memory for user={}; continuing with normal chat", userId, e);
            return true;
        }
    }

    private boolean isLongTermMemoryRecallRequest(String query) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return normalized.contains("根据你记住")
                || normalized.contains("根据记住的")
                || normalized.contains("按你记住")
                || normalized.contains("你记得我的")
                || normalized.contains("你记住的我的")
                || normalized.contains("我保存的偏好")
                || normalized.contains("已保存的偏好")
                || normalized.contains("记住的偏好")
                || normalized.contains("saved preference")
                || normalized.contains("saved response style")
                || normalized.contains("remembered preference")
                || normalized.contains("what do you remember about me")
                || normalized.contains("based on my active saved preference")
                || normalized.contains("based on my saved preference");
    }

    private boolean isSensitiveMemoryWriteRequest(String query) {
        String compact = query.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        boolean memoryIntent = compact.startsWith("请记住")
                || compact.startsWith("记住")
                || compact.startsWith("remember")
                || compact.startsWith("pleaseremember")
                || compact.startsWith("keepinmind");
        return memoryIntent && memoryPrivacyService.scan(query).hasBlockingFindings();
    }

    private boolean isForgetAllMemoryRequest(String query) {
        String compact = query.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        boolean memoryScope = compact.contains("记忆")
                || compact.contains("长期记忆")
                || compact.contains("你记住的")
                || compact.contains("所有记忆")
                || compact.contains("全部记忆")
                || compact.contains("allmemories")
                || compact.contains("memory");
        boolean deleteIntent = compact.contains("全部删除")
                || compact.contains("全部清除")
                || compact.contains("清空")
                || compact.contains("删除所有")
                || compact.contains("删掉所有")
                || compact.contains("全部删")
                || compact.contains("forgetall")
                || compact.contains("deleteall")
                || compact.contains("clearall");
        boolean correctionDelete = compact.contains("这些记忆都不对")
                || compact.contains("这些都不对")
                || compact.contains("全部不对");
        return (memoryScope && deleteIntent) || correctionDelete;
    }

    private AiChatResponse buildErrorResponse(String message) {
        AiChatResponse response = new AiChatResponse(message, new HashMap<>());
        response.setChatMode("ERROR");
        response.setDegraded(true);
        response.setDegradationReason("所有服务路径均不可用");
        return response;
    }

    private static class TokenTrackingCallback implements StreamCallback {
        private final StreamCallback delegate;
        private final StringBuilder emitted = new StringBuilder();
        private volatile boolean hasTokens = false;
        private volatile boolean completed = false;

        TokenTrackingCallback(StreamCallback delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onToken(String token) {
            hasTokens = true;
            emitted.append(token);
            delegate.onToken(token);
        }

        @Override
        public void onProgress(String step, String detail) {
            delegate.onProgress(step, detail);
        }

        @Override
        public void onComplete(AiChatResponse response) {
            completed = true;
            delegate.onComplete(response);
        }

        @Override
        public void onError(String errorMessage) {
            delegate.onError(errorMessage);
        }

        boolean hasEmittedTokens() {
            return hasTokens;
        }

        boolean isCompleted() {
            return completed;
        }

        String getEmittedContent() {
            return emitted.toString();
        }
    }
}
