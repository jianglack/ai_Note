package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryReplaySampleReviewServiceTest {

    @Test
    void activeReplayDatasetIsReviewedAsSimulationBaselineButNotRealUserPilot() {
        MemoryReplayDataset dataset = MemoryReplayDatasetLoader.loadActiveDataset();

        MemoryReplaySampleReviewService.SampleReviewReport report =
                MemoryReplaySampleReviewService.review(dataset);

        assertThat(report.totalCases()).isEqualTo(dataset.cases().size());
        assertThat(report.approvedActiveEvaluationCases()).isEqualTo(dataset.cases().size());
        assertThat(report.quarantinedCases()).isZero();
        assertThat(report.realUserApprovedCases()).isZero();
        assertThat(report.realUserPilotReady()).isFalse();
        assertThat(report.gateFailures()).contains("real_user_pilot_requires_50_approved_samples");
        assertThat(report.sourceCounts()).containsEntry("synthetic_seed", 200L);
        assertThat(report.sourceCounts()).containsEntry("simulated_realistic", 1800L);
        assertThat(report.records()).allSatisfy(record -> {
            assertThat(record.approvedForActiveEvaluation()).isTrue();
            assertThat(record.eligibleAsRealUserSample()).isFalse();
            assertThat(record.intakeBucket()).isEqualTo("simulation_baseline");
            assertThat(record.flags()).isEmpty();
        });
    }

    @Test
    void unsafeOrUnderReviewedSamplesAreQuarantined() {
        MemoryReplayDataset dataset = new MemoryReplayDataset(
                List.of(
                        replayCase(
                                "replay_real_user_email",
                                "用户说他常用 alice@example.org 接收提醒。",
                                false),
                        replayCase(
                                "replay_sim_pending_review",
                                "记住：以后默认用简洁中文回答。",
                                true)),
                List.of(
                        new MemoryReplayDataset.ManifestEntry(
                                "replay_real_user_email",
                                "real_user_anonymized",
                                "import:local-export:2026-07-08",
                                "real_user_importer",
                                List.of("chinese_stable_preference"),
                                List.of("zh"),
                                "real-conversation-1",
                                "",
                                "approved",
                                "",
                                "2026-07-08T00:00:00Z"),
                        new MemoryReplayDataset.ManifestEntry(
                                "replay_sim_pending_review",
                                "simulated_realistic",
                                "generator:test",
                                "power_user",
                                List.of("chinese_stable_preference"),
                                List.of("zh"),
                                "sim-conversation-1",
                                "redaction-simulated-v1",
                                "pending_review",
                                "consistency_reviewer_v1",
                                "2026-07-08T00:00:00Z")));

        MemoryReplaySampleReviewService.SampleReviewReport report =
                MemoryReplaySampleReviewService.review(dataset);

        assertThat(report.approvedActiveEvaluationCases()).isZero();
        assertThat(report.quarantinedCases()).isEqualTo(2);
        assertThat(report.realUserApprovedCases()).isZero();
        assertThat(report.records())
                .filteredOn(record -> record.caseId().equals("replay_real_user_email"))
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.intakeBucket()).isEqualTo("quarantine");
                    assertThat(record.approvedForActiveEvaluation()).isFalse();
                    assertThat(record.eligibleAsRealUserSample()).isFalse();
                    assertThat(record.flags())
                            .contains("contains_email", "missing_redaction_report_id", "missing_reviewer");
                });
        assertThat(report.records())
                .filteredOn(record -> record.caseId().equals("replay_sim_pending_review"))
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.intakeBucket()).isEqualTo("quarantine");
                    assertThat(record.approvedForActiveEvaluation()).isFalse();
                    assertThat(record.eligibleAsRealUserSample()).isFalse();
                    assertThat(record.flags()).contains("review_not_approved");
                });
    }

    private MemoryReplayEvaluationService.MemoryReplayCase replayCase(String id,
                                                                      String userMessage,
                                                                      boolean expectedCaptureAllowed) {
        return new MemoryReplayEvaluationService.MemoryReplayCase(
                id,
                userMessage,
                "ok",
                expectedCaptureAllowed,
                expectedCaptureAllowed ? "ALLOW_IMPLICIT_LOW_CONFIDENCE" : "DENY_TRANSIENT",
                expectedCaptureAllowed ? "preference" : "",
                false,
                expectedCaptureAllowed ? "stable preference" : "",
                expectedCaptureAllowed ? List.of("explicit_memory_request") : List.of());
    }
}
