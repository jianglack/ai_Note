package com.ainote.app.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiChatResponseTest {

    @Test
    void degradedFieldsDefaultToNonDegraded() {
        AiChatResponse response = new AiChatResponse("hello", Map.of());

        assertThat(response.isDegraded()).isFalse();
        assertThat(response.getChatMode()).isNull();
        assertThat(response.getDegradationReason()).isNull();
    }

    @Test
    void canSetDegradedFields() {
        AiChatResponse response = new AiChatResponse("fallback content", Map.of());

        response.setDegraded(true);
        response.setChatMode("FALLBACK");
        response.setDegradationReason("Agent timeout");

        assertThat(response.isDegraded()).isTrue();
        assertThat(response.getChatMode()).isEqualTo("FALLBACK");
        assertThat(response.getDegradationReason()).isEqualTo("Agent timeout");
    }

    @Test
    void chatModeAgentIsNotDegraded() {
        AiChatResponse response = new AiChatResponse("agent reply", Map.of());

        response.setDegraded(false);
        response.setChatMode("AGENT");

        assertThat(response.isDegraded()).isFalse();
        assertThat(response.getChatMode()).isEqualTo("AGENT");
        assertThat(response.getDegradationReason()).isNull();
    }
}
