package com.ainote.app.service;

import com.ainote.app.config.MemoryProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class LlmMemorySignalAdvisor implements MemorySignalAdvisor {

    static final String PROMPT_VERSION = "memory-advisor-v2";

    private static final Logger log = LoggerFactory.getLogger(LlmMemorySignalAdvisor.class);
    private static final Set<String> ALLOWED_SIGNALS = Set.of(
            "advisor_preference_signal",
            "advisor_fact_signal",
            "advisor_interaction_style_signal",
            "advisor_project_context_signal");
    private static final Set<String> ALLOWED_MEMORY_TYPES = Set.of(
            "fact",
            "preference",
            "style",
            "project_context",
            "none");

    private final MemoryProperties memoryProperties;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public LlmMemorySignalAdvisor(MemoryProperties memoryProperties,
                                  ChatModel chatModel,
                                  ObjectMapper objectMapper) {
        this.memoryProperties = memoryProperties;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    @Override
    public AdvisorResult advise(MemoryCapturePolicy.CaptureRequest request) {
        if (!memoryProperties.getCapture().getAdvisor().isEnabled()) {
            return AdvisorResult.unavailable(List.of("advisor_disabled"), "advisor disabled");
        }
        try {
            ChatResponse response = chatModel.chat(ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(systemPrompt()),
                            UserMessage.from(userPrompt(request))))
                    .build());
            return parse(response.aiMessage().text());
        } catch (Exception e) {
            log.warn("memory_advisor_event=failed error={} message={}",
                    e.getClass().getSimpleName(), e.getMessage());
            return AdvisorResult.unavailable(List.of("advisor_failed"), e.getClass().getSimpleName());
        }
    }

    private AdvisorResult parse(String responseText) throws Exception {
        JsonNode root = objectMapper.readTree(cleanJson(responseText));
        boolean shouldCapture = root.path("should_capture").asBoolean(false);
        String memoryType = normalizeMemoryType(root.path("memory_type").asText("none"));
        double confidence = root.path("confidence").asDouble(0.0);
        String reason = root.path("reason").asText("");
        List<String> signals = allowedSignals(root.path("signals"));

        if (!ALLOWED_MEMORY_TYPES.contains(memoryType)) {
            return AdvisorResult.noCapture("none", confidence, List.of("advisor_invalid_type"), reason);
        }
        if (!shouldCapture || "none".equals(memoryType)) {
            return AdvisorResult.noCapture(memoryType, confidence, signals, reason);
        }
        if (confidence < memoryProperties.getCapture().getAdvisor().getMinConfidence()) {
            List<String> lowConfidenceSignals = new ArrayList<>(signals);
            lowConfidenceSignals.add("advisor_low_confidence");
            return AdvisorResult.noCapture(memoryType, confidence, lowConfidenceSignals, reason);
        }
        return AdvisorResult.capture(memoryType, confidence, signals, reason);
    }

    private String cleanJson(String responseText) {
        String cleaned = responseText == null ? "" : responseText.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    private List<String> allowedSignals(JsonNode signalsNode) {
        if (!signalsNode.isArray()) {
            return List.of();
        }
        List<String> signals = new ArrayList<>();
        for (JsonNode signalNode : signalsNode) {
            String signal = signalNode.asText("");
            if (ALLOWED_SIGNALS.contains(signal)) {
                signals.add(signal);
            }
        }
        return List.copyOf(signals);
    }

    private String normalizeMemoryType(String memoryType) {
        return memoryType == null ? "none" : memoryType.trim().toLowerCase(Locale.ROOT);
    }

    static String canonicalPromptTemplate() {
        return systemPromptTemplate() + "\n\n" + userPromptTemplate();
    }

    private static String systemPromptTemplate() {
        return "Memory advisor prompt version: " + PROMPT_VERSION + ". "
                + "You classify whether a user turn contains stable long-term memory signals. "
                + "Return strict JSON only.";
    }

    private String systemPrompt() {
        return systemPromptTemplate();
    }

    private String userPrompt(MemoryCapturePolicy.CaptureRequest request) {
        return userPromptTemplate().formatted(safe(request == null ? null : request.userMessage()),
                safe(request == null ? null : request.aiResponse()));
    }

    private static String userPromptTemplate() {
        return """
                Decide whether the USER_MESSAGE contains a stable long-term memory signal.

                Safety rules:
                - Never capture RAG or reference_only content.
                - Never capture selected note or selected-note summaries as user profile memory.
                - Never capture one-off instructions such as "this time" or "for this reply".
                - Never capture delete, confirm, cancel, or tool operation requests.
                - Do not capture incidental uses of remember in writing tasks, grammar checks, stories, lyrics, examples, or questions about how to remember something.
                - Capture fact only for stable user/workspace/project facts that the user asks the system to remember, such as "remember that my timezone is UTC+8".
                - Use style for durable instructions about how the assistant should answer, such as tone, format, detail level, language, humor, or summary shape.
                - Use preference for durable product/task preferences that are not answer style.
                - Only capture stable user preference, stable user fact, interaction style, or explicitly labeled project context.

                Return strict JSON with:
                {
                  "should_capture": true|false,
                  "memory_type": "fact"|"preference"|"style"|"project_context"|"none",
                  "confidence": 0.0,
                  "signals": ["advisor_fact_signal"|"advisor_preference_signal"|"advisor_interaction_style_signal"|"advisor_project_context_signal"],
                  "reason": "short reason"
                }

                USER_MESSAGE:
                %s

                ASSISTANT_OUTPUT:
                %s
                """;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
