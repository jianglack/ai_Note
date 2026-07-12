package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryCandidateExtractorTest {

    private final MemoryCandidateExtractor extractor = new MemoryCandidateExtractor();
    private final MemoryCapturePolicy policy = new MemoryCapturePolicy();

    @Test
    void extractsExplicitAnswerFormatPreferenceAsStyleCandidate() {
        MemoryCapturePolicy.CaptureRequest request =
                new MemoryCapturePolicy.CaptureRequest("user-1", "remember: I prefer concise answers.", "Noted.");
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(request);

        List<MemoryCandidateExtractor.MemoryCandidate> candidates = extractor.extract(request, decision);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).memoryType()).isEqualTo("style");
        assertThat(candidates.get(0).category()).isEqualTo("preference");
    }

    @Test
    void extractsRealChineseShortNoteTitlePreferenceAsStyleCandidate() {
        MemoryCapturePolicy.CaptureRequest request =
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "\u6211\u5e0c\u671b\u7b14\u8bb0\u6807\u9898\u5c3d\u91cf\u77ed\u3002",
                        "\u5df2\u8bb0\u5f55\u3002");
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(request);

        List<MemoryCandidateExtractor.MemoryCandidate> candidates = extractor.extract(request, decision);

        assertThat(decision.allowed()).isTrue();
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).memoryType()).isEqualTo("style");
        assertThat(candidates.get(0).category()).isEqualTo("preference");
    }

    @Test
    void extractsExplicitRememberPreferenceCandidate() {
        MemoryCapturePolicy.CaptureRequest request =
                new MemoryCapturePolicy.CaptureRequest("user-1", "记住，我希望你以后用中文回答", "好的");
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(request);

        List<MemoryCandidateExtractor.MemoryCandidate> candidates = extractor.extract(request, decision);

        assertThat(candidates).hasSize(1);
        MemoryCandidateExtractor.MemoryCandidate candidate = candidates.get(0);
        assertThat(candidate.category()).isEqualTo("preference");
        assertThat(candidate.memoryType()).isEqualTo("style");
        assertThat(candidate.content()).contains("用中文回答");
        assertThat(candidate.confidence()).isGreaterThanOrEqualTo(0.9);
        assertThat(candidate.evidenceExcerpt()).contains("记住");
        assertThat(candidate.correction()).isFalse();
    }

    @Test
    void stripsChineseRememberColonWithoutLeavingLeadingPunctuation() {
        MemoryCapturePolicy.CaptureRequest request =
                new MemoryCapturePolicy.CaptureRequest(
                        "user-1",
                        "请记住：我以后默认希望你用中文回答，并且技术问题先给结论，再给关键理由。",
                        "好的");
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(request);

        List<MemoryCandidateExtractor.MemoryCandidate> candidates = extractor.extract(request, decision);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).content()).startsWith("我以后默认希望你用中文回答");
        assertThat(candidates.get(0).content()).doesNotStartWith("：");
        assertThat(candidates.get(0).content()).doesNotContain("请记住");
    }

    @Test
    void marksExplicitCorrectionCandidate() {
        MemoryCapturePolicy.CaptureRequest request = new MemoryCapturePolicy.CaptureRequest(
                "user-1",
                "记住，我不再希望你用英文回复，请改为用中文回复",
                "好的");
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(request);

        List<MemoryCandidateExtractor.MemoryCandidate> candidates = extractor.extract(request, decision);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).correction()).isTrue();
        assertThat(candidates.get(0).content()).contains("用中文回复");
    }

    @Test
    void extractsImplicitStyleCorrectionCandidate() {
        MemoryCapturePolicy.CaptureRequest request = new MemoryCapturePolicy.CaptureRequest(
                "user-1",
                "现在不要这么俏皮，要严肃深刻",
                "收到，切换模式。");
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(request);

        List<MemoryCandidateExtractor.MemoryCandidate> candidates = extractor.extract(request, decision);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).memoryType()).isEqualTo("style");
        assertThat(candidates.get(0).category()).isEqualTo("preference");
        assertThat(candidates.get(0).correction()).isTrue();
        assertThat(candidates.get(0).content()).isEqualTo("希望交互风格严肃深刻，避免俏皮");
    }

    @Test
    void normalizesChineseNotButInsteadCorrectionCandidate() {
        MemoryCapturePolicy.CaptureRequest request = new MemoryCapturePolicy.CaptureRequest(
                "user-1",
                "更正一下：我不是默认希望先详细解释，而是希望先给结论，再补充必要细节。请以后按这个记。",
                "收到。");
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(request);

        List<MemoryCandidateExtractor.MemoryCandidate> candidates = extractor.extract(request, decision);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).memoryType()).isEqualTo("style");
        assertThat(candidates.get(0).category()).isEqualTo("preference");
        assertThat(candidates.get(0).correction()).isTrue();
        assertThat(candidates.get(0).content()).isEqualTo("希望先给结论，再补充必要细节");
    }

    @Test
    void extractsStableStylePreferenceCandidate() {
        MemoryCapturePolicy.CaptureRequest request = new MemoryCapturePolicy.CaptureRequest(
                "user-1",
                "以后回答请严肃一些，少开玩笑。",
                "收到。");
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(request);

        List<MemoryCandidateExtractor.MemoryCandidate> candidates = extractor.extract(request, decision);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).memoryType()).isEqualTo("style");
        assertThat(candidates.get(0).category()).isEqualTo("preference");
        assertThat(candidates.get(0).scope()).isEqualTo("user");
        assertThat(candidates.get(0).content()).isEqualTo("希望交互风格严肃一些，少开玩笑");
        assertThat(candidates.get(0).correction()).isFalse();
        assertThat(candidates.get(0).decisionType())
                .isEqualTo(MemoryCapturePolicy.DecisionType.ALLOW_IMPLICIT_LOW_CONFIDENCE.name());
        assertThat(candidates.get(0).policyReason()).isEqualTo("implicit_interaction_style");
        assertThat(candidates.get(0).policySignals()).contains("stable_style_preference");
    }

    @Test
    void extractsProjectContextCandidate() {
        MemoryCapturePolicy.CaptureRequest request = new MemoryCapturePolicy.CaptureRequest(
                "user-1",
                "项目上下文：这个项目是一个 AI 笔记系统。",
                "已记录。");
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(request);

        List<MemoryCandidateExtractor.MemoryCandidate> candidates = extractor.extract(request, decision);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).memoryType()).isEqualTo("project_context");
        assertThat(candidates.get(0).category()).isEqualTo("project_context");
        assertThat(candidates.get(0).scope()).isEqualTo("project");
        assertThat(candidates.get(0).content()).isEqualTo("这个项目是一个 AI 笔记系统。");
    }

    @Test
    void extractsCorrectedProjectContextCandidate() {
        MemoryCapturePolicy.CaptureRequest request = new MemoryCapturePolicy.CaptureRequest(
                "user-1",
                "更正项目上下文：AiNote 的记忆系统现在已经完成任务 1 到任务 6，接下来要进入任务 7。",
                "已记录。");
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(request);

        List<MemoryCandidateExtractor.MemoryCandidate> candidates = extractor.extract(request, decision);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).memoryType()).isEqualTo("project_context");
        assertThat(candidates.get(0).category()).isEqualTo("project_context");
        assertThat(candidates.get(0).scope()).isEqualTo("project");
        assertThat(candidates.get(0).content()).contains("任务 7");
    }

    @Test
    void deniedPolicyProducesNoCandidates() {
        MemoryCapturePolicy.CaptureRequest request =
                new MemoryCapturePolicy.CaptureRequest("user-1", "取消", "已取消");
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(request);

        assertThat(extractor.extract(request, decision)).isEmpty();
    }
}
