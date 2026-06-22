package com.ainote.app.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AiController SSE Timeout alignment tests")
class AiControllerSseTimeoutTest {

    @Test
    @DisplayName("agentTimeoutSeconds=300 returns SSE timeout 330000ms")
    void sseTimeout_default300s_returns330000() {
        AiController controller = createControllerWithTimeout(300);
        assertThat(controller.calculateSseTimeoutMillis()).isEqualTo(330_000L);
    }

    @Test
    @DisplayName("agentTimeoutSeconds=60 returns SSE timeout 90000ms")
    void sseTimeout_60s_returns90000() {
        AiController controller = createControllerWithTimeout(60);
        assertThat(controller.calculateSseTimeoutMillis()).isEqualTo(90_000L);
    }

    @Test
    @DisplayName("agentTimeoutSeconds=0 returns SSE timeout 30000ms")
    void sseTimeout_0s_returns30000() {
        AiController controller = createControllerWithTimeout(0);
        assertThat(controller.calculateSseTimeoutMillis()).isEqualTo(30_000L);
    }

    private AiController createControllerWithTimeout(int timeoutSeconds) {
        AiController controller = new AiController(
                null, null, null, null, null,
                null, null, null, null, null, null);
        ReflectionTestUtils.setField(controller, "agentTimeoutSeconds", timeoutSeconds);
        return controller;
    }
}
