package com.ainote.app.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 统一 LLM 调用包装层
 * 为 Embedding、Rerank、记忆提取等非 Agent 的 LLM 调用提供熔断保护
 * Agent 模型的熔断通过 ResilientChatModel 装饰器实现
 */
@Service
public class ResilientLlmService {

    private static final Logger log = LoggerFactory.getLogger(ResilientLlmService.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final EmbeddingModel embeddingModel;
    private final ScoringModel scoringModel;

    public ResilientLlmService(
            CircuitBreakerRegistry circuitBreakerRegistry,
            EmbeddingModel embeddingModel,
            @Autowired(required = false) ScoringModel scoringModel) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.embeddingModel = embeddingModel;
        this.scoringModel = scoringModel;
    }

    /**
     * 带熔断的 Embedding 调用（单条）
     * 降级：返回 null，调用方需检查
     */
    public Response<Embedding> embed(TextSegment segment) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("embedding-model");
        Supplier<Response<Embedding>> supplier = CircuitBreaker.decorateSupplier(cb,
                () -> embeddingModel.embed(segment));
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("[embedding-model] Embedding failed (CB state: {}): {}",
                    cb.getState(), e.getMessage());
            return null;
        }
    }

    /**
     * 带熔断的 Embedding 调用（批量）
     * 降级：返回空列表
     */
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("embedding-model");
        Supplier<Response<List<Embedding>>> supplier = CircuitBreaker.decorateSupplier(cb,
                () -> embeddingModel.embedAll(segments));
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("[embedding-model] Batch embedding failed (CB state: {}): {}",
                    cb.getState(), e.getMessage());
            return Response.from(Collections.emptyList());
        }
    }

    /**
     * 带熔断的 Rerank 调用
     * 降级：返回 null，调用方跳过 rerank
     */
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        if (scoringModel == null) return null;

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("rerank-model");
        Supplier<Response<List<Double>>> supplier = CircuitBreaker.decorateSupplier(cb,
                () -> scoringModel.scoreAll(segments, query));
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("[rerank-model] Rerank failed (CB state: {}), skipping: {}",
                    cb.getState(), e.getMessage());
            return null;
        }
    }

    /**
     * 检查 Embedding 模型是否可用（熔断器是否开放）
     */
    public boolean isEmbeddingAvailable() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("embedding-model");
        return cb.getState() != CircuitBreaker.State.OPEN;
    }

    /**
     * 检查 Rerank 模型是否可用
     */
    public boolean isRerankAvailable() {
        if (scoringModel == null) return false;
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("rerank-model");
        return cb.getState() != CircuitBreaker.State.OPEN;
    }
}
