package com.ainote.app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JiTokenServiceTest {

    private JiTokenService service;

    @BeforeEach
    void setUp() {
        service = new JiTokenService();
    }

    @Test
    void countTokens_nullOrEmpty_returnsZero() {
        assertThat(service.countTokens(null)).isZero();
        assertThat(service.countTokens("")).isZero();
    }

    @Test
    void countTokens_asciiText_returnsPositiveCount() {
        assertThat(service.countTokens("Hello, this is a token counting test.")).isPositive();
    }

    @Test
    void countTokens_sameText_returnsStableCachedValue() {
        String text = "Repeatable token counting should be deterministic.";

        int first = service.countTokens(text);
        int second = service.countTokens(text);

        assertThat(second).isEqualTo(first);
    }
}
