package com.ainote.app.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.cohere.CohereScoringModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;

@Configuration
public class LangChain4jConfig {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jConfig.class);

    @Value("${app.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${app.deepseek.model:deepseek-chat}")
    private String deepseekModel;

    @Value("${app.deepseek.base-url:https://api.deepseek.com/v1}")
    private String deepseekBaseUrl;

    @Value("${app.deepseek.temperature:0.2}")
    private double deepseekTemperature;

    @Value("${app.deepseek.max-tokens:4000}")
    private int deepseekMaxTokens;

    @Value("${app.deepseek.embedding-model}")
    private String deepseekEmbeddingModel;

    @Value("${app.deepseek.embedding-base-url}")
    private String deepseekEmbeddingBaseUrl;

    @Value("${app.cohere.api-key:}")
    private String cohereApiKey;

    @Value("${app.cohere.model:rerank-v3.5}")
    private String cohereModel;

    @Value("${app.embedding.dimension:1024}")
    private int embeddingDimension;

    @Value("${app.llm.log-io:false}")
    private boolean logIo;

    private void requireNonEmpty(String value, String keyName) {
        if (value == null || value.isBlank()) {
            log.error("Required API key [{}] is not configured. Set the environment variable and restart.", keyName);
            throw new IllegalStateException("Required API key [" + keyName + "] is not configured");
        }
    }

    @Bean
    @Primary
    public ChatModel chatLanguageModel(
            @Autowired(required = false) List<ChatModelListener> listeners,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry
    ) {
        requireNonEmpty(deepseekApiKey, "DEEPSEEK_API_KEY");
        ChatModel primary = buildDeepSeekChatModel("primary", listeners);

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("chat-model");
        Retry retry = retryRegistry.retry("chat-model");
        log.info("Chat model wrapped with Retry + CircuitBreaker [chat-model], fallback: none");
        return new ResilientChatModel(primary, null, cb, retry, "chat-model");
    }

    @Bean
    public ChatModel agentChatModel(
            @Autowired(required = false) List<ChatModelListener> listeners,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry
    ) {
        requireNonEmpty(deepseekApiKey, "DEEPSEEK_API_KEY");
        ChatModel primary = buildDeepSeekChatModel("agent", listeners);

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("agent-model");
        Retry retry = retryRegistry.retry("agent-model");
        log.info("Agent model wrapped with Retry + CircuitBreaker [agent-model], fallback: none");
        return new ResilientChatModel(primary, null, cb, retry, "agent-model");
    }

    @Bean
    public StreamingChatModel streamingChatModel(
            @Autowired(required = false) List<ChatModelListener> listeners
    ) {
        requireNonEmpty(deepseekApiKey, "DEEPSEEK_API_KEY");
        log.info("Initializing streaming ChatModel (DeepSeek) with model: {}, baseUrl: {}",
                deepseekModel, deepseekBaseUrl);
        var builder = OpenAiStreamingChatModel.builder()
                .baseUrl(deepseekBaseUrl)
                .apiKey(deepseekApiKey)
                .modelName(deepseekModel)
                .temperature(deepseekTemperature)
                .maxTokens(deepseekMaxTokens)
                .timeout(Duration.ofSeconds(120))
                .logRequests(logIo)
                .logResponses(logIo);

        if (listeners != null && !listeners.isEmpty()) {
            builder.listeners(listeners);
        }
        return builder.build();
    }

    @Bean
    @Primary
    public EmbeddingModel deepSeekEmbeddingModel() {
        requireNonEmpty(deepseekApiKey, "DEEPSEEK_API_KEY");
        log.info("Initializing DeepSeek EmbeddingModel with model: {}, baseUrl: {}",
                deepseekEmbeddingModel, deepseekEmbeddingBaseUrl);
        // DeepSeek exposes an OpenAI-compatible API; this client does not call OpenAI.
        return OpenAiEmbeddingModel.builder()
                .baseUrl(deepseekEmbeddingBaseUrl)
                .apiKey(deepseekApiKey)
                .modelName(deepseekEmbeddingModel)
                .timeout(Duration.ofSeconds(30))
                .logRequests(logIo)
                .logResponses(logIo)
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> pgVectorEmbeddingStore(DataSource dataSource) {
        log.info("Initializing PgVectorEmbeddingStore with dimension: {}", embeddingDimension);
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table("langchain4j_embeddings")
                .dimension(embeddingDimension)
                .createTable(true)
                .build();
    }

    @Bean
    public ScoringModel cohereScoringModel() {
        if (cohereApiKey == null || cohereApiKey.isEmpty()) {
            log.warn("Cohere API key not configured, reranking will be disabled");
            return null;
        }
        log.info("Initializing CohereScoringModel with model: {}", cohereModel);
        return CohereScoringModel.builder()
                .apiKey(cohereApiKey)
                .modelName(cohereModel)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    public ContentAggregator contentAggregator(ObjectProvider<ScoringModel> scoringModelProvider) {
        ScoringModel scoringModel = scoringModelProvider.getIfAvailable();
        if (scoringModel == null) {
            log.info("Using default content aggregation (no reranking)");
            return null;
        }
        log.info("Using ReRankingContentAggregator with Cohere");
        return ReRankingContentAggregator.builder()
                .scoringModel(scoringModel)
                .minScore(0.3)
                .build();
    }

    private ChatModel buildDeepSeekChatModel(String purpose, List<ChatModelListener> listeners) {
        log.info("Initializing {} ChatModel (DeepSeek) with model: {}, baseUrl: {}",
                purpose, deepseekModel, deepseekBaseUrl);
        // DeepSeek exposes an OpenAI-compatible API; this client does not call OpenAI.
        var builder = OpenAiChatModel.builder()
                .baseUrl(deepseekBaseUrl)
                .apiKey(deepseekApiKey)
                .modelName(deepseekModel)
                .temperature(deepseekTemperature)
                .maxTokens(deepseekMaxTokens)
                .timeout(Duration.ofSeconds(120))
                .logRequests(logIo)
                .logResponses(logIo);

        if (listeners != null && !listeners.isEmpty()) {
            builder.listeners(listeners);
        }
        return builder.build();
    }
}
