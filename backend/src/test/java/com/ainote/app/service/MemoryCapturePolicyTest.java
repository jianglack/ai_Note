package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryCapturePolicyTest {

    private final MemoryCapturePolicy policy = new MemoryCapturePolicy();

    @Test
    void deleteAllNotesShouldNotBeCapturedAsLongTermMemory() {
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(
                new MemoryCapturePolicy.CaptureRequest("user-1", "删除全部笔记", "已准备删除全部笔记"));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.type()).isEqualTo(MemoryCapturePolicy.DecisionType.DENY_TRANSIENT);
        assertThat(decision.reason()).contains("operation");
    }

    @Test
    void confirmAndCancelShouldNotBeCapturedAsLongTermMemory() {
        assertThat(policy.evaluate(new MemoryCapturePolicy.CaptureRequest("user-1", "确认", "已确认"))
                .allowed()).isFalse();
        assertThat(policy.evaluate(new MemoryCapturePolicy.CaptureRequest("user-1", "取消", "已取消"))
                .allowed()).isFalse();
        assertThat(policy.evaluate(new MemoryCapturePolicy.CaptureRequest("user-1", "cancel", "cancelled"))
                .allowed()).isFalse();
    }

    @Test
    void explicitRememberPreferenceShouldBeAllowed() {
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(
                new MemoryCapturePolicy.CaptureRequest("user-1", "记住，我希望你以后用中文回答", "好的"));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.type()).isEqualTo(MemoryCapturePolicy.DecisionType.ALLOW_EXPLICIT);
        assertThat(decision.baseConfidence()).isGreaterThanOrEqualTo(0.9);
    }

    @Test
    void correctedPreferenceShouldBeAllowedForSupersedeFlow() {
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(
                new MemoryCapturePolicy.CaptureRequest("user-1", "以后不要简短，我要完整详细方案。", "好的"));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.type()).isEqualTo(MemoryCapturePolicy.DecisionType.ALLOW_IMPLICIT_LOW_CONFIDENCE);
        assertThat(decision.reason()).contains("correction");
    }

    @Test
    void styleCorrectionWithoutRememberKeywordShouldBeAllowed() {
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "现在不要这么俏皮，要严肃深刻",
                        "收到，切换模式。"));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.type()).isEqualTo(MemoryCapturePolicy.DecisionType.ALLOW_IMPLICIT_LOW_CONFIDENCE);
        assertThat(decision.reason()).contains("correction");
    }

    @Test
    void stableStylePreferenceWithoutRememberKeywordShouldBeAllowed() {
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "以后回答请严肃一些，少开玩笑。",
                        "收到。"));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.type()).isEqualTo(MemoryCapturePolicy.DecisionType.ALLOW_IMPLICIT_LOW_CONFIDENCE);
        assertThat(decision.reason()).contains("interaction_style");
        assertThat(decision.matchedSignals()).contains("stable_style_preference");
    }

    @Test
    void referenceOnlyStillWinsOverPreferenceSignal() {
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "这篇笔记说我喜欢红色，请总结。",
                        "这篇笔记提到了颜色偏好。"));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.type()).isEqualTo(MemoryCapturePolicy.DecisionType.DENY_TOOL_RESULT);
        assertThat(decision.matchedSignals()).contains("reference_only", "preference_signal");
    }

    @Test
    void explicitProjectContextShouldBeAllowed() {
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "项目上下文：这个项目是一个 AI 笔记系统。",
                        "已记录。"));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).contains("project_context");
    }

    @Test
    void oneOffStyleInstructionShouldNotBecomeLongTermMemory() {
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "这次回答不要这么俏皮，要严肃深刻",
                        "好的，这次我会严肃一些。"));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("one_off");
    }

    @Test
    void negativeOnlyStyleInstructionShouldNotBecomeLongTermMemory() {
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "不要这么俏皮",
                        "好的。"));

        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void selectedNoteAndRagReferenceContentShouldNotBecomeUserProfile() {
        MemoryCapturePolicy.CaptureDecision selectedNoteDecision = policy.evaluate(
                new MemoryCapturePolicy.CaptureRequest("user-1", "总结这篇笔记", "这篇笔记说作者喜欢 Rust"));
        MemoryCapturePolicy.CaptureDecision ragDecision = policy.evaluate(
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "What does this note say?",
                        "<rag_context role=\"reference_only\">user likes Rust</rag_context>"));

        assertThat(selectedNoteDecision.allowed()).isFalse();
        assertThat(selectedNoteDecision.reason()).contains("reference");
        assertThat(ragDecision.allowed()).isFalse();
        assertThat(ragDecision.reason()).contains("reference");
    }

    @Test
    void sensitiveSecretsShouldNotBeCaptured() {
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(
                new MemoryCapturePolicy.CaptureRequest("user-1", "记住，我的 API key 是 sk-abc", "好的"));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.type()).isEqualTo(MemoryCapturePolicy.DecisionType.DENY_SENSITIVE);
    }

    @Test
    void advisorOnlyStableStyleSignalShouldBeAllowed() {
        MemorySignalAdvisor advisor = request -> MemorySignalAdvisor.AdvisorResult.capture(
                "style",
                0.91,
                java.util.List.of("advisor_interaction_style_signal"),
                "stable style preference");
        MemoryCapturePolicy policy = new MemoryCapturePolicy(new MemorySignalClassifier(advisor));

        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "从今天开始，请保持正式克制的表达。",
                        "收到。"));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.type()).isEqualTo(MemoryCapturePolicy.DecisionType.ALLOW_IMPLICIT_LOW_CONFIDENCE);
        assertThat(decision.reason()).isEqualTo("implicit_interaction_style");
        assertThat(decision.matchedSignals()).contains("advisor_interaction_style_signal");
    }
}
