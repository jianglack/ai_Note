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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class LlmMemorySignalAdvisor implements MemoryAdvisorRawSignalAdvisor {

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
    private final MemorySignalClassifier policyPrecheckClassifier;
    private final MemoryCapturePolicy policyBaseline;
    private final MemoryCandidateExtractor policyBaselineCandidateExtractor;
    private final MemoryMetricsService memoryMetricsService;

    public LlmMemorySignalAdvisor(MemoryProperties memoryProperties,
                                  ChatModel chatModel,
                                  ObjectMapper objectMapper) {
        this(memoryProperties, chatModel, objectMapper, MemoryMetricsService.noop());
    }

    @Autowired
    public LlmMemorySignalAdvisor(MemoryProperties memoryProperties,
                                  ChatModel chatModel,
                                  ObjectMapper objectMapper,
                                  MemoryMetricsService memoryMetricsService) {
        this.memoryProperties = memoryProperties;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.policyPrecheckClassifier = new MemorySignalClassifier(MemorySignalAdvisor.disabled());
        this.policyBaseline = new MemoryCapturePolicy(new MemorySignalClassifier(MemorySignalAdvisor.disabled()));
        this.policyBaselineCandidateExtractor = new MemoryCandidateExtractor();
        this.memoryMetricsService = memoryMetricsService == null ? MemoryMetricsService.noop() : memoryMetricsService;
    }

    @Override
    public AdvisorResult advise(MemoryCapturePolicy.CaptureRequest request) {
        return adviseRaw(request).finalResult();
    }

    @Override
    public MemoryAdvisorRawResult adviseRaw(MemoryCapturePolicy.CaptureRequest request) {
        long startedAt = System.nanoTime();
        if (!memoryProperties.getCapture().getAdvisor().isEnabled()) {
            memoryMetricsService.recordAdvisorSkipped("advisor_disabled");
            AdvisorResult disabled = AdvisorResult.unavailable(List.of("advisor_disabled"), "advisor disabled");
            return MemoryAdvisorRawResult.fromFinal(disabled);
        }
        MemoryAdvisorRawResult policyPrecheck = policyPrecheck(request);
        if (policyPrecheck != null) {
            memoryMetricsService.recordAdvisorResult(policyPrecheck.available(), policyPrecheck.parsed(),
                    "policy_precheck", elapsedMs(startedAt));
            return policyPrecheck;
        }
        try {
            ChatResponse response = chatModel.chat(ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(systemPrompt()),
                            UserMessage.from(userPrompt(request))))
                    .build());
            MemoryAdvisorRawResult result = applyPolicyBaseline(parseRaw(response.aiMessage().text()), request);
            memoryMetricsService.recordAdvisorResult(result.available(), result.parsed(),
                    result.failureReason(), elapsedMs(startedAt));
            return result;
        } catch (Exception e) {
            log.warn("memory_advisor_event=failed error={} message={}",
                    e.getClass().getSimpleName(), e.getMessage());
            AdvisorResult unavailable = AdvisorResult.unavailable(List.of("advisor_failed"), e.getClass().getSimpleName());
            memoryMetricsService.recordAdvisorResult(false, false, e.getClass().getSimpleName(), elapsedMs(startedAt));
            return new MemoryAdvisorRawResult(false, false, false, "none", 0.0,
                    unavailable.signals(), "", e.getClass().getSimpleName(), unavailable);
        }
    }

    private MemoryAdvisorRawResult policyPrecheck(MemoryCapturePolicy.CaptureRequest request) {
        MemorySignalClassifier.SignalClassification signals = policyPrecheckClassifier.classify(request);
        String reason = policyPrecheckReason(signals);
        if (reason.isBlank()) {
            return null;
        }
        List<String> precheckSignals = new ArrayList<>(signals.matchedSignals());
        precheckSignals.add("advisor_policy_precheck");
        AdvisorResult result = AdvisorResult.noCapture(
                "none",
                1.0,
                List.copyOf(precheckSignals),
                "policy_precheck:" + reason);
        return MemoryAdvisorRawResult.fromFinal(result);
    }

    private String policyPrecheckReason(MemorySignalClassifier.SignalClassification signals) {
        if (signals == null) {
            return "";
        }
        if (!signals.validUser() || signals.blankMessage()) {
            return "blank_or_missing_user";
        }
        if (signals.sensitive()) {
            return "sensitive_content";
        }
        if (signals.forgetRequest()) {
            return "forget_request";
        }
        if (signals.referenceOnly()) {
            return "reference_context_not_profile";
        }
        if (signals.transientOperation()) {
            return "operation_or_confirmation";
        }
        if (signals.oneOffScope()) {
            return "one_off_instruction";
        }
        if (signals.taskOnlyContent()) {
            return "task_only_content";
        }
        if (signals.roleOverridePrompt()) {
            return "role_override_prompt";
        }
        if (signals.assistantFeedback()) {
            return "assistant_feedback";
        }
        return "";
    }

    private MemoryAdvisorRawResult applyPolicyBaseline(MemoryAdvisorRawResult rawResult,
                                                       MemoryCapturePolicy.CaptureRequest request) {
        if (rawResult == null || rawResult.finalResult() == null || !rawResult.available() || !rawResult.parsed()) {
            return rawResult;
        }
        MemoryCapturePolicy.CaptureDecision baselineDecision = policyBaseline.evaluate(request);
        if (rawResult.finalResult().shouldCapture()) {
            if (baselineDecision.allowed()) {
                return applyPolicyBaselineTypeNormalization(rawResult, request, baselineDecision);
            }
            return applyPolicyBaselineReject(rawResult, baselineDecision);
        }
        if (!baselineDecision.allowed()) {
            return rawResult;
        }
        return applyPolicyBaselineAllow(rawResult, request, baselineDecision);
    }

    private MemoryAdvisorRawResult applyPolicyBaselineTypeNormalization(
            MemoryAdvisorRawResult rawResult,
            MemoryCapturePolicy.CaptureRequest request,
            MemoryCapturePolicy.CaptureDecision baselineDecision) {
        String baselineType = baselineMemoryType(request, baselineDecision);
        if (baselineType.equals(rawResult.finalResult().memoryType())) {
            return rawResult;
        }
        List<String> normalizedSignals = new ArrayList<>(rawResult.finalResult().signals());
        normalizedSignals.addAll(baselineDecision.matchedSignals());
        normalizedSignals.add("advisor_policy_baseline_type_normalize");
        AdvisorResult normalizedResult = AdvisorResult.capture(
                baselineType,
                rawResult.finalResult().confidence(),
                List.copyOf(normalizedSignals),
                rawResult.finalResult().reason());
        return new MemoryAdvisorRawResult(
                rawResult.available(),
                rawResult.parsed(),
                rawResult.rawShouldCapture(),
                rawResult.rawMemoryType(),
                rawResult.rawConfidence(),
                rawResult.rawSignals(),
                rawResult.rawReason(),
                rawResult.failureReason(),
                normalizedResult);
    }

    private MemoryAdvisorRawResult applyPolicyBaselineReject(
            MemoryAdvisorRawResult rawResult,
            MemoryCapturePolicy.CaptureDecision baselineDecision) {
        List<String> gatedSignals = new ArrayList<>(rawResult.finalResult().signals());
        gatedSignals.addAll(baselineDecision.matchedSignals());
        gatedSignals.add("advisor_policy_baseline_reject");
        AdvisorResult gatedResult = AdvisorResult.noCapture(
                "none",
                rawResult.rawConfidence(),
                List.copyOf(gatedSignals),
                "policy_baseline_reject:" + baselineDecision.reason());
        return new MemoryAdvisorRawResult(
                rawResult.available(),
                rawResult.parsed(),
                rawResult.rawShouldCapture(),
                rawResult.rawMemoryType(),
                rawResult.rawConfidence(),
                rawResult.rawSignals(),
                rawResult.rawReason(),
                rawResult.failureReason(),
                gatedResult);
    }

    private MemoryAdvisorRawResult applyPolicyBaselineAllow(
            MemoryAdvisorRawResult rawResult,
            MemoryCapturePolicy.CaptureRequest request,
            MemoryCapturePolicy.CaptureDecision baselineDecision) {
        List<String> baselineSignals = new ArrayList<>(rawResult.finalResult().signals());
        baselineSignals.addAll(baselineDecision.matchedSignals());
        baselineSignals.add("advisor_policy_baseline_allow");
        AdvisorResult baselineResult = AdvisorResult.capture(
                baselineMemoryType(request, baselineDecision),
                Math.max(rawResult.finalResult().confidence(), baselineDecision.baseConfidence()),
                List.copyOf(baselineSignals),
                "policy_baseline_allow:" + baselineDecision.reason());
        return new MemoryAdvisorRawResult(
                rawResult.available(),
                rawResult.parsed(),
                rawResult.rawShouldCapture(),
                rawResult.rawMemoryType(),
                rawResult.rawConfidence(),
                rawResult.rawSignals(),
                rawResult.rawReason(),
                rawResult.failureReason(),
                baselineResult);
    }

    private String baselineMemoryType(MemoryCapturePolicy.CaptureRequest request,
                                      MemoryCapturePolicy.CaptureDecision baselineDecision) {
        List<MemoryCandidateExtractor.MemoryCandidate> candidates =
                policyBaselineCandidateExtractor.extract(request, baselineDecision);
        if (!candidates.isEmpty()) {
            return candidates.get(0).memoryType();
        }
        String reason = baselineDecision.reason();
        if ("project_context".equals(reason)) {
            return "project_context";
        }
        if (reason != null && reason.contains("style")) {
            return "style";
        }
        if (reason != null && reason.contains("preference")) {
            return "preference";
        }
        return "fact";
    }

    private MemoryAdvisorRawResult parseRaw(String responseText) throws Exception {
        JsonNode root = objectMapper.readTree(cleanJson(responseText));
        boolean rawShouldCapture = root.path("should_capture").asBoolean(false);
        String rawMemoryType = normalizeMemoryType(root.path("memory_type").asText("none"));
        double rawConfidence = root.path("confidence").asDouble(0.0);
        String rawReason = root.path("reason").asText("");
        List<String> rawSignals = allowedSignals(root.path("signals"));
        AdvisorResult finalResult;

        if (!ALLOWED_MEMORY_TYPES.contains(rawMemoryType)) {
            finalResult = AdvisorResult.noCapture("none", rawConfidence, List.of("advisor_invalid_type"), rawReason);
        } else if (!rawShouldCapture || "none".equals(rawMemoryType)) {
            finalResult = AdvisorResult.noCapture(rawMemoryType, rawConfidence, rawSignals, rawReason);
        } else if (rawConfidence < memoryProperties.getCapture().getAdvisor().getMinConfidence()) {
            List<String> lowConfidenceSignals = new ArrayList<>(rawSignals);
            lowConfidenceSignals.add("advisor_low_confidence");
            finalResult = AdvisorResult.noCapture(rawMemoryType, rawConfidence, lowConfidenceSignals, rawReason);
        } else {
            finalResult = AdvisorResult.capture(rawMemoryType, rawConfidence, rawSignals, rawReason);
        }
        return new MemoryAdvisorRawResult(true, true, rawShouldCapture, rawMemoryType, rawConfidence,
                rawSignals, rawReason, "", finalResult);
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
                - Never capture secrets, credentials, API keys, access tokens, session tokens, passwords, or product keys.
                - Never capture external or general-world facts as user memory, even when the user says to remember them.
                - Never capture facts embedded in writing, editing, rewriting, proofreading, translation, role-play, game, or prompt-generation tasks unless they are explicitly labeled as user/project memory.
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

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
