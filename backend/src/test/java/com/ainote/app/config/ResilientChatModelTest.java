package com.ainote.app.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResilientChatModelTest {

    @Test
    void chat_rateLimitException_retriesAndReturnsPrimarySuccess() {
        ChatModel primary = mock(ChatModel.class);
        ChatModel fallback = mock(ChatModel.class);
        ChatResponse success = response("primary ok");
        when(primary.chat(any(ChatRequest.class)))
                .thenThrow(new RateLimitException("HTTP 429"))
                .thenReturn(success);
        ResilientChatModel model = model(primary, fallback, 3);

        ChatResponse result = model.chat(request());

        assertThat(result).isSameAs(success);
        verify(primary, times(2)).chat(any(ChatRequest.class));
        verify(fallback, never()).chat(any(ChatRequest.class));
    }

    @Test
    void chat_internalServerException_exhaustsRetriesThenFallsBack() {
        ChatModel primary = mock(ChatModel.class);
        ChatModel fallback = mock(ChatModel.class);
        ChatResponse fallbackResponse = response("fallback ok");
        when(primary.chat(any(ChatRequest.class)))
                .thenThrow(new InternalServerException("HTTP 500"));
        when(fallback.chat(any(ChatRequest.class))).thenReturn(fallbackResponse);
        ResilientChatModel model = model(primary, fallback, 2);

        ChatResponse result = model.chat(request());

        assertThat(result).isSameAs(fallbackResponse);
        verify(primary, times(2)).chat(any(ChatRequest.class));
        verify(fallback).chat(any(ChatRequest.class));
    }

    @Test
    void chat_withoutFallback_rethrowsAfterRetries() {
        ChatModel primary = mock(ChatModel.class);
        when(primary.chat(any(ChatRequest.class)))
                .thenThrow(new RateLimitException("HTTP 429"));
        ResilientChatModel model = model(primary, null, 2);

        assertThatThrownBy(() -> model.chat(request()))
                .isInstanceOf(RateLimitException.class);
        verify(primary, times(2)).chat(any(ChatRequest.class));
    }

    private static ResilientChatModel model(ChatModel primary, ChatModel fallback, int maxAttempts) {
        Retry retry = Retry.of("test-retry", RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(Duration.ZERO)
                .retryExceptions(RateLimitException.class, InternalServerException.class)
                .build());
        CircuitBreaker circuitBreaker = CircuitBreaker.of("test-cb", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(100)
                .build());
        return new ResilientChatModel(primary, fallback, circuitBreaker, retry, "test");
    }

    private static ChatRequest request() {
        return ChatRequest.builder()
                .messages(List.of(UserMessage.from("hello")))
                .build();
    }

    private static ChatResponse response(String text) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .build();
    }
}
