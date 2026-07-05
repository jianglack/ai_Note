package com.ainote.app.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemoryCapturePolicy {

    private final MemorySignalClassifier signalClassifier;

    @Autowired
    public MemoryCapturePolicy(MemorySignalClassifier signalClassifier) {
        this.signalClassifier = signalClassifier;
    }

    MemoryCapturePolicy() {
        this(new MemorySignalClassifier());
    }

    public CaptureDecision evaluate(CaptureRequest request) {
        MemorySignalClassifier.SignalClassification signals = signalClassifier.classify(request);
        List<String> matchedSignals = signals.matchedSignals();

        if (!signals.validUser() || signals.blankMessage()) {
            return CaptureDecision.deny(DecisionType.DENY_TRANSIENT, "blank_or_missing_user", matchedSignals);
        }
        if (signals.sensitive()) {
            return CaptureDecision.deny(DecisionType.DENY_SENSITIVE, "sensitive_content", matchedSignals);
        }
        if (signals.forgetRequest()) {
            return CaptureDecision.deny(DecisionType.FORGET_REQUEST, "forget_request", matchedSignals);
        }
        if (signals.referenceOnly()) {
            return CaptureDecision.deny(DecisionType.DENY_TOOL_RESULT, "reference_context_not_profile", matchedSignals);
        }
        if (signals.transientOperation()) {
            return CaptureDecision.deny(DecisionType.DENY_TRANSIENT, "operation_or_confirmation", matchedSignals);
        }
        if (signals.oneOffScope()) {
            return CaptureDecision.deny(DecisionType.DENY_TRANSIENT, "one_off_instruction", matchedSignals);
        }
        if (signals.assistantFeedback()) {
            return CaptureDecision.deny(DecisionType.DENY_TRANSIENT, "assistant_feedback", matchedSignals);
        }
        if (signals.explicitRemember()) {
            return CaptureDecision.allow(DecisionType.ALLOW_EXPLICIT, "explicit_memory", 0.95, matchedSignals);
        }
        if (signals.correctionSignal()) {
            String reason = signals.interactionStyleSignal()
                    ? "correction_interaction_style"
                    : "correction_preference";
            return CaptureDecision.allow(DecisionType.ALLOW_IMPLICIT_LOW_CONFIDENCE, reason, 0.85, matchedSignals);
        }
        if (signals.interactionStyleSignal()) {
            return CaptureDecision.allow(
                    DecisionType.ALLOW_IMPLICIT_LOW_CONFIDENCE,
                    "implicit_interaction_style",
                    0.75,
                    matchedSignals);
        }
        if (signals.projectContextSignal()) {
            return CaptureDecision.allow(
                    DecisionType.ALLOW_IMPLICIT_LOW_CONFIDENCE,
                    "project_context",
                    0.8,
                    matchedSignals);
        }
        if (signals.preferenceSignal()) {
            return CaptureDecision.allow(
                    DecisionType.ALLOW_IMPLICIT_LOW_CONFIDENCE,
                    "implicit_preference",
                    0.65,
                    matchedSignals);
        }
        return CaptureDecision.deny(DecisionType.DENY_TRANSIENT, "no_stable_user_memory_signal", matchedSignals);
    }

    public enum DecisionType {
        ALLOW_EXPLICIT,
        ALLOW_IMPLICIT_LOW_CONFIDENCE,
        DENY_TRANSIENT,
        DENY_SENSITIVE,
        DENY_TOOL_RESULT,
        FORGET_REQUEST
    }

    public record CaptureRequest(String userId, String userMessage, String aiResponse) {
    }

    public record CaptureDecision(DecisionType type,
                                  boolean allowed,
                                  String reason,
                                  double baseConfidence,
                                  List<String> matchedSignals) {
        public CaptureDecision {
            matchedSignals = matchedSignals == null ? List.of() : List.copyOf(matchedSignals);
        }

        static CaptureDecision allow(DecisionType type, String reason, double baseConfidence) {
            return allow(type, reason, baseConfidence, List.of());
        }

        static CaptureDecision allow(DecisionType type,
                                     String reason,
                                     double baseConfidence,
                                     List<String> matchedSignals) {
            return new CaptureDecision(type, true, reason, baseConfidence, matchedSignals);
        }

        static CaptureDecision deny(DecisionType type, String reason) {
            return deny(type, reason, List.of());
        }

        static CaptureDecision deny(DecisionType type, String reason, List<String> matchedSignals) {
            return new CaptureDecision(type, false, reason, 0.0, matchedSignals);
        }
    }
}
