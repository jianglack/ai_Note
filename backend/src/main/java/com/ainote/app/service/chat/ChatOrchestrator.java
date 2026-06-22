package com.ainote.app.service.chat;

import com.ainote.app.agent.guardrail.GuardrailResult;
import com.ainote.app.agent.guardrail.InputGuardrail;
import com.ainote.app.model.AiChatResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

@Service
public class ChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);

    private final ChatStrategy agentStrategy;
    private final ChatStrategy fallbackStrategy;
    private final CircuitBreaker circuitBreaker;
    private final ChatMetrics chatMetrics;
    private final InputGuardrail inputGuardrail;

    public ChatOrchestrator(
            AgentChatStrategy agentStrategy,
            ReadOnlyFallbackChatService fallbackStrategy,
            CircuitBreakerRegistry circuitBreakerRegistry,
            ChatMetrics chatMetrics,
            InputGuardrail inputGuardrail) {
        this.agentStrategy = agentStrategy;
        this.fallbackStrategy = fallbackStrategy;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("agent-chat");
        this.chatMetrics = chatMetrics;
        this.inputGuardrail = inputGuardrail;
    }

    public AiChatResponse chat(String query, List<String> noteIds, String userId) {
        chatMetrics.recordRequest();

        GuardrailResult guardResult = inputGuardrail.check(query);
        if (!guardResult.passed()) {
            return buildRejectionResponse(guardResult.reason());
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
        chatMetrics.recordRequest();

        GuardrailResult guardResult = inputGuardrail.check(query);
        if (!guardResult.passed()) {
            AiChatResponse rejection = buildRejectionResponse(guardResult.reason());
            callback.onToken(guardResult.reason());
            callback.onComplete(rejection);
            return;
        }

        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            chatMetrics.recordCircuitOpen();
            fallbackStrategy.chatStream(query, noteIds, userId, callback);
            return;
        }

        TokenTrackingCallback trackingCallback = new TokenTrackingCallback(callback);
        long startTime = System.currentTimeMillis();

        try {
            circuitBreaker.executeRunnable(
                    () -> agentStrategy.chatStream(query, noteIds, userId, trackingCallback));
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
