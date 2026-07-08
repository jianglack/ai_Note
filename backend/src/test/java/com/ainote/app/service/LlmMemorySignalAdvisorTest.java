package com.ainote.app.service;

import com.ainote.app.config.MemoryProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmMemorySignalAdvisorTest {

    private MemoryProperties memoryProperties;
    private ChatModel chatModel;
    private LlmMemorySignalAdvisor advisor;

    @BeforeEach
    void setUp() {
        memoryProperties = new MemoryProperties();
        chatModel = mock(ChatModel.class);
        advisor = new LlmMemorySignalAdvisor(memoryProperties, chatModel, new ObjectMapper());
    }

    @Test
    void disabledAdvisorDoesNotCallModel() {
        MemorySignalAdvisor.AdvisorResult result = advisor.advise(request("从今天开始，请保持正式克制的表达。"));

        assertThat(result.available()).isFalse();
        assertThat(result.signals()).contains("advisor_disabled");
        verify(chatModel, never()).chat(any(ChatRequest.class));
    }

    @Test
    void parsesHighConfidencePreferenceAdvice() {
        memoryProperties.getCapture().getAdvisor().setEnabled(true);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse("""
                {"should_capture":true,"memory_type":"style","confidence":0.91,
                 "signals":["advisor_interaction_style_signal"],"reason":"stable style preference"}
                """));

        MemorySignalAdvisor.AdvisorResult result = advisor.advise(request("从今天开始，请保持正式克制的表达。"));

        assertThat(result.available()).isTrue();
        assertThat(result.shouldCapture()).isTrue();
        assertThat(result.memoryType()).isEqualTo("style");
        assertThat(result.confidence()).isEqualTo(0.91);
        assertThat(result.signals()).containsExactly("advisor_interaction_style_signal");
        assertThat(result.reason()).contains("stable style preference");
    }

    @Test
    void parsesHighConfidenceFactAdvice() {
        memoryProperties.getCapture().getAdvisor().setEnabled(true);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse("""
                {"should_capture":true,"memory_type":"fact","confidence":0.91,
                 "signals":["advisor_fact_signal"],"reason":"stable user fact"}
                """));

        MemorySignalAdvisor.AdvisorResult result = advisor.advise(request("Remember that my timezone is UTC+8."));

        assertThat(result.available()).isTrue();
        assertThat(result.shouldCapture()).isTrue();
        assertThat(result.memoryType()).isEqualTo("fact");
        assertThat(result.signals()).containsExactly("advisor_fact_signal");
    }

    @Test
    void lowConfidenceAdviceIsNotCapturable() {
        memoryProperties.getCapture().getAdvisor().setEnabled(true);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse("""
                {"should_capture":true,"memory_type":"preference","confidence":0.4,
                 "signals":["advisor_preference_signal"],"reason":"weak signal"}
                """));

        MemorySignalAdvisor.AdvisorResult result = advisor.advise(request("以后看情况吧。"));

        assertThat(result.available()).isTrue();
        assertThat(result.shouldCapture()).isFalse();
        assertThat(result.signals()).contains("advisor_low_confidence");
    }

    @Test
    void malformedJsonReturnsObservableFailure() {
        memoryProperties.getCapture().getAdvisor().setEnabled(true);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse("not json"));

        MemorySignalAdvisor.AdvisorResult result = advisor.advise(request("从今天开始，请保持正式克制的表达。"));

        assertThat(result.available()).isFalse();
        assertThat(result.shouldCapture()).isFalse();
        assertThat(result.signals()).contains("advisor_failed");
    }

    @Test
    void promptContainsReferenceOnlySafetyInstruction() {
        memoryProperties.getCapture().getAdvisor().setEnabled(true);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse("""
                {"should_capture":false,"memory_type":"none","confidence":0.0,
                 "signals":[],"reason":"not memory"}
                """));

        advisor.advise(request("从今天开始，请保持正式克制的表达。"));

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel).chat(requestCaptor.capture());
        String prompt = requestCaptor.getValue().messages().stream()
                .filter(UserMessage.class::isInstance)
                .map(message -> ((UserMessage) message).singleText())
                .findFirst()
                .orElse("");
        String systemPrompt = requestCaptor.getValue().messages().stream()
                .filter(SystemMessage.class::isInstance)
                .map(message -> ((SystemMessage) message).text())
                .findFirst()
                .orElse("");
        assertThat(systemPrompt).contains(LlmMemorySignalAdvisor.PROMPT_VERSION);
        assertThat(prompt)
                .contains("reference_only")
                .contains("selected note")
                .contains("RAG")
                .contains("one-off")
                .contains("Do not capture incidental uses of remember")
                .contains("\"memory_type\": \"fact\"|\"preference\"|\"style\"|\"project_context\"|\"none\"");
    }

    private MemoryCapturePolicy.CaptureRequest request(String userMessage) {
        return new MemoryCapturePolicy.CaptureRequest("user-1", userMessage, "收到。");
    }

    private static ChatResponse chatResponse(String text) {
        return ChatResponse.builder()
                .aiMessage(dev.langchain4j.data.message.AiMessage.from(text))
                .build();
    }
}
