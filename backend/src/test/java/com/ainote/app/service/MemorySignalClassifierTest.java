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
}
