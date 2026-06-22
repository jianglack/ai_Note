package com.ainote.app.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 弹性装饰器：包装 ChatModel，提供 Retry + CircuitBreaker + Fallback
 * Retry 在最内层（先重试），CircuitBreaker 在外层（重试耗尽后熔断计数）
 * 用于 AiServices.builder().chatModel(resilientModel)
 */
public class ResilientChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(ResilientChatModel.class);

    private final ChatModel primary;
    private final ChatModel fallback;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final String name;

    public ResilientChatModel(ChatModel primary, ChatModel fallback,
                               CircuitBreaker circuitBreaker, Retry retry, String name) {
        this.primary = primary;
        this.fallback = fallback;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.name = name;
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        try {
            // Retry wraps the inner call; CircuitBreaker wraps the retry.
            // Order: CB → Retry → actual call (retry exhausted failures count toward CB)
            return CircuitBreaker.decorateSupplier(circuitBreaker,
                    Retry.decorateSupplier(retry, () -> primary.chat(chatRequest))
            ).get();
        } catch (Exception e) {
            if (fallback != null) {
                log.warn("[{}] Primary model failed after retries (CB state: {}), falling back: {}",
                        name, circuitBreaker.getState(), e.getMessage());
                return fallback.chat(chatRequest);
            }
            throw e;
        }
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public Retry getRetry() {
        return retry;
    }
}
