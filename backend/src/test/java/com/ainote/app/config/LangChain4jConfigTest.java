package com.ainote.app.config;

import dev.langchain4j.model.scoring.ScoringModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LangChain4jConfigTest {

    @Test
    void failFast_whenDeepseekKeyEmpty() {
        LangChain4jConfig config = new LangChain4jConfig();
        setField(config, "deepseekApiKey", "");

        assertThatThrownBy(() -> config.chatLanguageModel(null, null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failFast_agentChatModel_whenDeepseekKeyEmpty() {
        LangChain4jConfig config = new LangChain4jConfig();
        setField(config, "deepseekApiKey", "");

        assertThatThrownBy(() -> config.agentChatModel(null, null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failFast_streamingChatModel_whenDeepseekKeyEmpty() {
        LangChain4jConfig config = new LangChain4jConfig();
        setField(config, "deepseekApiKey", "");

        assertThatThrownBy(() -> config.streamingChatModel(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failFast_embeddingModel_whenDeepseekKeyEmpty() {
        LangChain4jConfig config = new LangChain4jConfig();
        setField(config, "deepseekApiKey", "");

        assertThatThrownBy(config::deepSeekEmbeddingModel)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void contentAggregatorIsOptionalWhenScoringModelIsMissing() {
        LangChain4jConfig config = new LangChain4jConfig();
        var beanFactory = new StaticListableBeanFactory();

        assertThat(config.contentAggregator(beanFactory.getBeanProvider(ScoringModel.class)))
                .isNull();
    }

    @Test
    void llmIoLogging_isPropertyGatedInsteadOfAlwaysEnabled() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/ainote/app/config/LangChain4jConfig.java"));

        assertThat(source).contains("@Value(\"${app.llm.log-io:false}\")");
        assertThat(source).contains(".logRequests(logIo)");
        assertThat(source).contains(".logResponses(logIo)");
        assertThat(source).doesNotContain(".logRequests(true)");
        assertThat(source).doesNotContain(".logResponses(true)");
    }

    @Test
    void applicationYaml_setsRequestBodyLimits() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(yaml).contains("max-file-size:");
        assertThat(yaml).contains("max-request-size:");
        assertThat(yaml).contains("max-http-form-post-size:");
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = LangChain4jConfig.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
