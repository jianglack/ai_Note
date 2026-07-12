package com.ainote.app.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptLoaderTest {

    private PromptLoader loader;

    @BeforeEach
    void setUp() {
        loader = new PromptLoader();
    }

    @Test
    void load_existingPrompt_returnsContentAndCachesIt() {
        String first = loader.load("concept-extraction.txt");
        String second = loader.load("concept-extraction.txt");

        assertThat(first).isNotBlank();
        assertThat(second).isSameAs(first);
    }

    @Test
    void load_missingPrompt_throwsRuntimeException() {
        assertThatThrownBy(() -> loader.load("missing-prompt.txt"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cannot load prompt: missing-prompt.txt");
    }

    @Test
    void format_existingTemplate_formatsArguments() {
        assertThat(loader.format("test-format.txt", "Alice", 3).trim())
                .isEqualTo("Hello Alice, count=3");
    }

    @Test
    void fallbackPromptForbidsInventingMissingUserMemory() {
        assertThat(loader.load("fallback-system.txt"))
                .contains("没有对应的 <user_memory>")
                .contains("不得假装知道、猜测或编造用户偏好和项目背景");
    }
}
