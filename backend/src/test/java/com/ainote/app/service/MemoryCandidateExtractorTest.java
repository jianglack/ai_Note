package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryCandidateExtractorTest {

    private final MemoryCandidateExtractor extractor = new MemoryCandidateExtractor();
    private final MemoryCapturePolicy policy = new MemoryCapturePolicy();

    @Test
    void extractsExplicitRememberPreferenceCandidate() {
        MemoryCapturePolicy.CaptureRequest request =
                new MemoryCapturePolicy.CaptureRequest("user-1", "记住，我希望你以后用中文回答", "好的");
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(request);

        List<MemoryCandidateExtractor.MemoryCandidate> candidates = extractor.extract(request, decision);

        assertThat(candidates).hasSize(1);
        MemoryCandidateExtractor.MemoryCandidate candidate = candidates.get(0);
        assertThat(candidate.category()).isEqualTo("preference");
        assertThat(candidate.memoryType()).isEqualTo("preference");
        assertThat(candidate.content()).contains("用中文回答");
        assertThat(candidate.confidence()).isGreaterThanOrEqualTo(0.9);
        assertThat(candidate.evidenceExcerpt()).contains("记住");
        assertThat(candidate.correction()).isFalse();
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
    void deniedPolicyProducesNoCandidates() {
        MemoryCapturePolicy.CaptureRequest request =
                new MemoryCapturePolicy.CaptureRequest("user-1", "取消", "已取消");
        MemoryCapturePolicy.CaptureDecision decision = policy.evaluate(request);

        assertThat(extractor.extract(request, decision)).isEmpty();
    }
}
