package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemorySignalClassifierTest {

    private final MemorySignalClassifier classifier = new MemorySignalClassifier();

    @Test
    void classifiesStableStylePreferenceWithoutRememberKeyword() {
        MemorySignalClassifier.SignalClassification signals = classifier.classify(
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "以后回答请严肃一些，少开玩笑。",
                        "收到。"));

        assertThat(signals.interactionStyleSignal()).isTrue();
        assertThat(signals.preferenceSignal()).isTrue();
        assertThat(signals.oneOffScope()).isFalse();
        assertThat(signals.matchedSignals()).contains("stable_style_preference");
    }

    @Test
    void classifiesOneOffScopeBeforeStylePreference() {
        MemorySignalClassifier.SignalClassification signals = classifier.classify(
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "这次回答请严肃一些。",
                        "收到。"));

        assertThat(signals.oneOffScope()).isTrue();
        assertThat(signals.interactionStyleSignal()).isTrue();
        assertThat(signals.matchedSignals()).contains("one_off_scope", "one_off_style_preference");
    }

    @Test
    void classifiesReferenceOnlyEvenWhenPreferenceWordsAppear() {
        MemorySignalClassifier.SignalClassification signals = classifier.classify(
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "这篇笔记说我喜欢红色，请总结。",
                        "这篇笔记提到了颜色偏好。"));

        assertThat(signals.referenceOnly()).isTrue();
        assertThat(signals.preferenceSignal()).isTrue();
        assertThat(signals.matchedSignals()).contains("reference_only");
    }

    @Test
    void classifiesExplicitProjectContext() {
        MemorySignalClassifier.SignalClassification signals = classifier.classify(
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "项目上下文：这个项目是一个 AI 笔记系统。",
                        "已记录。"));

        assertThat(signals.projectContextSignal()).isTrue();
        assertThat(signals.matchedSignals()).contains("project_context");
    }

    @Test
    void skipsAdvisorWhenHardDenySignalExists() {
        CapturingAdvisor advisor = new CapturingAdvisor(MemorySignalAdvisor.AdvisorResult.capture(
                "style",
                0.91,
                java.util.List.of("advisor_interaction_style_signal"),
                "stable style preference"));
        MemorySignalClassifier classifier = new MemorySignalClassifier(advisor);

        MemorySignalClassifier.SignalClassification signals = classifier.classify(
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "这篇笔记说我喜欢正式语气，请总结。",
                        "这篇笔记提到了语气偏好。"));

        assertThat(advisor.called()).isFalse();
        assertThat(signals.referenceOnly()).isTrue();
        assertThat(signals.matchedSignals()).doesNotContain("advisor_interaction_style_signal");
    }

    @Test
    void mergesHighConfidenceAdvisorStyleSignal() {
        MemorySignalClassifier classifier = new MemorySignalClassifier(
                new CapturingAdvisor(MemorySignalAdvisor.AdvisorResult.capture(
                        "style",
                        0.91,
                        java.util.List.of("advisor_interaction_style_signal"),
                        "stable style preference")));

        MemorySignalClassifier.SignalClassification signals = classifier.classify(
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "从今天开始，请保持正式克制的表达。",
                        "收到。"));

        assertThat(signals.interactionStyleSignal()).isTrue();
        assertThat(signals.preferenceSignal()).isTrue();
        assertThat(signals.matchedSignals()).contains("advisor_interaction_style_signal");
    }

    @Test
    void advisorFailureIsObservableButDoesNotAllowClassify() {
        MemorySignalClassifier classifier = new MemorySignalClassifier(
                new CapturingAdvisor(MemorySignalAdvisor.AdvisorResult.unavailable(
                        java.util.List.of("advisor_failed"),
                        "malformed advisor response")));

        MemorySignalClassifier.SignalClassification signals = classifier.classify(
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "从今天开始，请保持正式克制的表达。",
                        "收到。"));

        assertThat(signals.preferenceSignal()).isFalse();
        assertThat(signals.interactionStyleSignal()).isFalse();
        assertThat(signals.matchedSignals()).contains("advisor_failed");
    }

    @Test
    void classifiesPositiveFeedbackAboutThisAnswerAsAssistantFeedback() {
        MemorySignalClassifier.SignalClassification signals = classifier.classify(
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "I like this answer, thanks.",
                        "Glad it helped."));

        assertThat(signals.assistantFeedback()).isTrue();
        assertThat(signals.matchedSignals()).contains("assistant_feedback");
    }

    private static final class CapturingAdvisor implements MemorySignalAdvisor {
        private final AdvisorResult result;
        private boolean called;

        private CapturingAdvisor(AdvisorResult result) {
            this.result = result;
        }

        @Override
        public AdvisorResult advise(MemoryCapturePolicy.CaptureRequest request) {
            called = true;
            return result;
        }

        private boolean called() {
            return called;
        }
    }
}
