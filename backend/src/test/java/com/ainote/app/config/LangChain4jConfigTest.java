package com.ainote.app.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class LangChain4jConfigTest {

    @Test
    void failFast_whenDeepseekKeyEmpty() {
        LangChain4jConfig config = new LangChain4jConfig();
        setField(config, "deepseekApiKey", "");

        assertThrows(
                IllegalStateException.class,
                () -> config.chatLanguageModel(null, null, null)
        );
    }

    @Test
    void failFast_agentChatModel_whenDeepseekKeyEmpty() {
        LangChain4jConfig config = new LangChain4jConfig();
        setField(config, "deepseekApiKey", "");

        assertThrows(
                IllegalStateException.class,
                () -> config.agentChatModel(null, null, null)
        );
    }

    @Test
    void failFast_embeddingModel_whenDeepseekKeyEmpty() {
        LangChain4jConfig config = new LangChain4jConfig();
        setField(config, "deepseekApiKey", "");

        assertThrows(
                IllegalStateException.class,
                config::deepSeekEmbeddingModel
        );
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
