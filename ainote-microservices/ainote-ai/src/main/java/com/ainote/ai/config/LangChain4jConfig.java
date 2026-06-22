package com.ainote.ai.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @Value("${app.embedding.dimension:1024}")
    private int embeddingDimension;

    private void requireDeepSeekKey() {
        if (deepseekApiKey == null || deepseekApiKey.isBlank()) {
            log.error("Required API key [DEEPSEEK_API_KEY] is not configured. Set the environment variable and restart.");
            throw new IllegalStateException("Required API key [DEEPSEEK_API_KEY] is not configured");
        }
    }

    @Bean
    @Primary
    public ChatModel chatLanguageModel(
            @Autowired(required = false) List<ChatModelListener> listeners
    ) {
        requireDeepSeekKey();
        return new ResilientChatModel(buildDeepSeekChatModel("primary", listeners), null, "chat-model");
    }

    @Bean
    public ChatModel agentChatModel(
            @Autowired(required = false) List<ChatModelListener> listeners
    ) {
        requireDeepSeekKey();
        return new ResilientChatModel(buildDeepSeekChatModel("agent", listeners), null, "agent-model");
    }

    @Bean
    @Primary
    public EmbeddingModel deepSeekEmbeddingModel() {
        requireDeepSeekKey();
        log.info("Initializing DeepSeek EmbeddingModel with model: {}, baseUrl: {}",
                deepseekEmbeddingModel, deepseekEmbeddingBaseUrl);
        // DeepSeek exposes an OpenAI-compatible API; this client does not call OpenAI.
        return OpenAiEmbeddingModel.builder()
                .baseUrl(deepseekEmbeddingBaseUrl)
                .apiKey(deepseekApiKey)
                .modelName(deepseekEmbeddingModel)
                .timeout(Duration.ofSeconds(30))
                .logRequests(true)
                .logResponses(true)
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
    public ContentRetriever contentRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel) {
        log.info("Initializing EmbeddingStoreContentRetriever");
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(10)
                .minScore(0.5)
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
                .logRequests(true)
                .logResponses(true);

        if (listeners != null && !listeners.isEmpty()) {
            builder.listeners(listeners);
        }
        return builder.build();
    }
}
