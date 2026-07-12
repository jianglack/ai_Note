package com.ainote.app.service;

import com.ainote.app.entity.MemoryEvent;
import com.ainote.app.entity.MemoryReviewCase;
import com.ainote.app.entity.SemanticMemory;
import com.ainote.app.model.memory.MemoryFeedbackRequest;
import com.ainote.app.model.memory.MemoryReplayCandidateExportResponse;
import com.ainote.app.model.memory.MemoryReviewCaseListResponse;
import com.ainote.app.model.memory.MemoryReviewCaseResponse;
import com.ainote.app.model.memory.MemoryReviewDecisionRequest;
import com.ainote.app.repository.MemoryEventRepository;
import com.ainote.app.repository.MemoryReviewCaseRepository;
import com.ainote.app.repository.SemanticMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryReviewServiceTest {

    private SemanticMemoryRepository semanticMemoryRepository;
    private MemoryEventRepository memoryEventRepository;
    private MemoryReviewCaseRepository memoryReviewCaseRepository;
    private MemoryMetricsService memoryMetricsService;
    private MemoryReviewService service;

    @BeforeEach
    void setUp() {
        semanticMemoryRepository = mock(SemanticMemoryRepository.class);
        memoryEventRepository = mock(MemoryEventRepository.class);
        memoryReviewCaseRepository = mock(MemoryReviewCaseRepository.class);
        memoryMetricsService = mock(MemoryMetricsService.class);
        service = new MemoryReviewService(
                semanticMemoryRepository,
                memoryEventRepository,
                memoryReviewCaseRepository,
                new MemoryPrivacyService(),
                memoryMetricsService);
    }

    @Test
    void createFeedbackStoresPendingReviewCaseWithSnapshotsAndAuditEvent() {
        SemanticMemory memory = memory("user-1", 11L, "active");
        memory.setMetadataJson("{\"policy_source\":\"advisor\",\"capture_reason\":\"explicit\"}");
        memory.setSourceTraceId("trace-11");
        memory.setSourceMessageIds("[\"m1\",\"m2\"]");
        memory.setEvidenceExcerpt("用户说以后回答要简洁");
        when(semanticMemoryRepository.findByIdAndUserId(11L, "user-1")).thenReturn(Optional.of(memory));
        when(memoryReviewCaseRepository.save(any(MemoryReviewCase.class))).thenAnswer(invocation -> {
            MemoryReviewCase reviewCase = invocation.getArgument(0);
            reviewCase.setId(201L);
            reviewCase.setCreatedAt(LocalDateTime.parse("2026-07-09T10:00:00"));
            reviewCase.setUpdatedAt(LocalDateTime.parse("2026-07-09T10:00:00"));
            return reviewCase;
        });

        MemoryReviewCaseResponse response = service.createFeedback(
                "user-1",
                11L,
                new MemoryFeedbackRequest(
                        "wrong_memory",
                        "这条记忆反了",
                        "用户偏好简洁中文回答",
                        "preference",
                        true));

        assertThat(response.id()).isEqualTo(201L);
        assertThat(response.memoryId()).isEqualTo(11L);
        assertThat(response.feedbackType()).isEqualTo("wrong_memory");
        assertThat(response.status()).isEqualTo("pending_review");
        assertThat(response.memoryBeforeJson()).contains("\"content\":\"prefers markdown\"");
        assertThat(response.sourceContextJson()).contains("trace-11");
        assertThat(response.policySnapshotJson()).contains("policy_source");

        ArgumentCaptor<MemoryReviewCase> reviewCaptor = ArgumentCaptor.forClass(MemoryReviewCase.class);
        verify(memoryReviewCaseRepository).save(reviewCaptor.capture());
        assertThat(reviewCaptor.getValue().getUserId()).isEqualTo("user-1");
        assertThat(reviewCaptor.getValue().getMemoryId()).isEqualTo(11L);
        assertThat(reviewCaptor.getValue().getStatus()).isEqualTo("pending_review");

        ArgumentCaptor<MemoryEvent> eventCaptor = ArgumentCaptor.forClass(MemoryEvent.class);
        verify(memoryEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("FEEDBACK_REPORTED");
        assertThat(eventCaptor.getValue().getActor()).isEqualTo("user");
        assertThat(eventCaptor.getValue().getMemoryId()).isEqualTo(11L);
        verify(memoryMetricsService).recordFeedback("wrong_memory");
    }

    @Test
    void createFeedbackRejectsOtherUsersMemory() {
        when(semanticMemoryRepository.findByIdAndUserId(11L, "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createFeedback(
                "user-1",
                11L,
                new MemoryFeedbackRequest("wrong_memory", "wrong", null, null, null)))
                .isInstanceOf(NoSuchElementException.class);

        verify(memoryReviewCaseRepository, never()).save(any());
        verify(memoryEventRepository, never()).save(any());
    }

    @Test
    void createFeedbackRedactsSensitiveUserSuppliedText() {
        SemanticMemory memory = memory("user-1", 11L, "active");
        when(semanticMemoryRepository.findByIdAndUserId(11L, "user-1")).thenReturn(Optional.of(memory));
        when(memoryReviewCaseRepository.save(any(MemoryReviewCase.class))).thenAnswer(invocation -> {
            MemoryReviewCase reviewCase = invocation.getArgument(0);
            reviewCase.setId(201L);
            return reviewCase;
        });

        MemoryReviewCaseResponse response = service.createFeedback(
                "user-1",
                11L,
                new MemoryFeedbackRequest(
                        "wrong_memory",
                        "wrong email alice@example.com",
                        "phone 13812345678",
                        "preference",
                        true));

        assertThat(response.userComment()).contains("[REDACTED:email]").doesNotContain("alice@example.com");
        assertThat(response.expectedContent()).contains("[REDACTED:phone]").doesNotContain("13812345678");

        ArgumentCaptor<MemoryReviewCase> reviewCaptor = ArgumentCaptor.forClass(MemoryReviewCase.class);
        verify(memoryReviewCaseRepository).save(reviewCaptor.capture());
        assertThat(reviewCaptor.getValue().getUserComment()).doesNotContain("alice@example.com");
        assertThat(reviewCaptor.getValue().getExpectedContent()).doesNotContain("13812345678");
    }

    @Test
    void listReviewCasesUsesStatusQueueWithBoundedLimit() {
        MemoryReviewCase reviewCase = reviewCase(201L, "pending_review");
        when(memoryReviewCaseRepository.findByStatusOrderByCreatedAtAsc(
                eq("pending_review"), any(PageRequest.class)))
                .thenReturn(List.of(reviewCase));

        MemoryReviewCaseListResponse response = service.listReviewCases("pending_review", 500);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).id()).isEqualTo(201L);
        verify(memoryReviewCaseRepository).findByStatusOrderByCreatedAtAsc(
                eq("pending_review"), any(PageRequest.class));
    }

    @Test
    void rejectFeedbackMarksCaseRejectedWithoutMutatingMemory() {
        MemoryReviewCase reviewCase = reviewCase(202L, "pending_review");
        when(memoryReviewCaseRepository.findById(202L)).thenReturn(Optional.of(reviewCase));
        when(memoryReviewCaseRepository.save(reviewCase)).thenReturn(reviewCase);

        MemoryReviewCaseResponse response = service.decideReviewCase(
                202L,
                "admin-1",
                new MemoryReviewDecisionRequest("reject_feedback", "证据不足", null, null, null));

        assertThat(response.status()).isEqualTo("rejected");
        assertThat(response.reviewerId()).isEqualTo("admin-1");
        assertThat(response.reviewerDecision()).isEqualTo("reject_feedback");
        verify(semanticMemoryRepository, never()).save(any());

        ArgumentCaptor<MemoryEvent> eventCaptor = ArgumentCaptor.forClass(MemoryEvent.class);
        verify(memoryEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("REVIEW_REJECTED");
        assertThat(eventCaptor.getValue().getActor()).isEqualTo("admin");
    }

    @Test
    void disableMemoryAppliesAdminActionAndRecordsAuditEvent() {
        MemoryReviewCase reviewCase = reviewCase(203L, "pending_review");
        SemanticMemory memory = memory("user-1", 11L, "active");
        when(memoryReviewCaseRepository.findById(203L)).thenReturn(Optional.of(reviewCase));
        when(semanticMemoryRepository.findByIdAndUserId(11L, "user-1")).thenReturn(Optional.of(memory));
        when(semanticMemoryRepository.save(memory)).thenReturn(memory);
        when(memoryReviewCaseRepository.save(reviewCase)).thenReturn(reviewCase);

        MemoryReviewCaseResponse response = service.decideReviewCase(
                203L,
                "admin-1",
                new MemoryReviewDecisionRequest("disable_memory", "确认错记", null, null, null));

        assertThat(memory.getStatus()).isEqualTo("disabled");
        assertThat(response.status()).isEqualTo("actioned");
        assertThat(response.reviewerDecision()).isEqualTo("disable_memory");

        ArgumentCaptor<MemoryEvent> eventCaptor = ArgumentCaptor.forClass(MemoryEvent.class);
        verify(memoryEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("REVIEW_DISABLED");
        assertThat(eventCaptor.getValue().getBeforeJson()).contains("\"status\":\"active\"");
        assertThat(eventCaptor.getValue().getAfterJson()).contains("\"status\":\"disabled\"");
    }

    @Test
    void updateMemoryAppliesCorrectedFields() {
        MemoryReviewCase reviewCase = reviewCase(204L, "pending_review");
        SemanticMemory memory = memory("user-1", 11L, "active");
        when(memoryReviewCaseRepository.findById(204L)).thenReturn(Optional.of(reviewCase));
        when(semanticMemoryRepository.findByIdAndUserId(11L, "user-1")).thenReturn(Optional.of(memory));
        when(semanticMemoryRepository.save(memory)).thenReturn(memory);
        when(memoryReviewCaseRepository.save(reviewCase)).thenReturn(reviewCase);

        MemoryReviewCaseResponse response = service.decideReviewCase(
                204L,
                "admin-1",
                new MemoryReviewDecisionRequest(
                        "update_memory",
                        "修正偏好",
                        "用户偏好简洁中文回答",
                        "preference",
                        0.95));

        assertThat(memory.getContent()).isEqualTo("用户偏好简洁中文回答");
        assertThat(memory.getMemoryType()).isEqualTo("preference");
        assertThat(memory.getConfidence()).isEqualTo(0.95);
        assertThat(response.status()).isEqualTo("actioned");

        ArgumentCaptor<MemoryEvent> eventCaptor = ArgumentCaptor.forClass(MemoryEvent.class);
        verify(memoryEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("REVIEW_UPDATED");
    }

    @Test
    void updateMemoryRejectsSensitiveCorrectedContent() {
        MemoryReviewCase reviewCase = reviewCase(204L, "pending_review");
        SemanticMemory memory = memory("user-1", 11L, "active");
        when(memoryReviewCaseRepository.findById(204L)).thenReturn(Optional.of(reviewCase));
        when(semanticMemoryRepository.findByIdAndUserId(11L, "user-1")).thenReturn(Optional.of(memory));

        assertThatThrownBy(() -> service.decideReviewCase(
                204L,
                "admin-1",
                new MemoryReviewDecisionRequest(
                        "update_memory",
                        "reject sensitive correction",
                        "backup email alice@example.com",
                        "preference",
                        0.95)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sensitive data");

        verify(semanticMemoryRepository, never()).save(any());
        verify(memoryReviewCaseRepository, never()).save(any());
        verify(memoryEventRepository, never()).save(any());
    }

    @Test
    void approveReplayGeneratesStableReplayCandidateAndManifest() {
        MemoryReviewCase reviewCase = reviewCase(205L, "pending_review");
        reviewCase.setFeedbackType("should_not_remember");
        reviewCase.setExpectedCaptureAllowed(false);
        when(memoryReviewCaseRepository.findById(205L)).thenReturn(Optional.of(reviewCase));
        when(memoryReviewCaseRepository.save(reviewCase)).thenReturn(reviewCase);

        MemoryReviewCaseResponse response = service.decideReviewCase(
                205L,
                "admin-1",
                new MemoryReviewDecisionRequest("approve_replay", "加入回归", null, null, null));

        assertThat(response.status()).isEqualTo("approved_for_replay");
        assertThat(response.replayCaseId()).isEqualTo("review-205");
        assertThat(response.replayCaseJson()).contains("\"source\":\"human_review_feedback\"");
        assertThat(response.replayCaseJson()).contains("\"captureAllowed\":false");
        assertThat(response.manifestJson()).contains("\"reviewerId\":\"admin-1\"");

        ArgumentCaptor<MemoryEvent> eventCaptor = ArgumentCaptor.forClass(MemoryEvent.class);
        verify(memoryEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("REVIEW_APPROVED_FOR_REPLAY");
    }

    @Test
    void exportApprovedReplayCandidatesReturnsOnlyApprovedCases() {
        MemoryReviewCase reviewCase = reviewCase(206L, "approved_for_replay");
        reviewCase.setReplayCaseId("review-206");
        reviewCase.setReplayCaseJson("{\"id\":\"review-206\"}");
        reviewCase.setManifestJson("{\"reviewerId\":\"admin-1\"}");
        when(memoryReviewCaseRepository.findByStatusOrderByReviewedAtDesc(
                eq("approved_for_replay"), any(PageRequest.class)))
                .thenReturn(List.of(reviewCase));

        MemoryReplayCandidateExportResponse response = service.exportApprovedReplayCandidates(500);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).replayCaseId()).isEqualTo("review-206");
        assertThat(response.items().get(0).replayCaseJson()).contains("review-206");
        verify(memoryReviewCaseRepository).findByStatusOrderByReviewedAtDesc(
                eq("approved_for_replay"), any(PageRequest.class));
    }

    private static SemanticMemory memory(String userId, Long id, String status) {
        SemanticMemory memory = new SemanticMemory();
        memory.setId(id);
        memory.setUserId(userId);
        memory.setCategory("preference");
        memory.setMemoryType("preference");
        memory.setScope("user");
        memory.setStatus(status);
        memory.setContent("prefers markdown");
        memory.setConfidence(0.9);
        memory.setSource("ai_extracted");
        return memory;
    }

    private static MemoryReviewCase reviewCase(Long id, String status) {
        MemoryReviewCase reviewCase = new MemoryReviewCase();
        reviewCase.setId(id);
        reviewCase.setUserId("user-1");
        reviewCase.setMemoryId(11L);
        reviewCase.setFeedbackType("wrong_memory");
        reviewCase.setUserComment("wrong");
        reviewCase.setExpectedContent("用户偏好简洁中文回答");
        reviewCase.setExpectedMemoryType("preference");
        reviewCase.setExpectedCaptureAllowed(true);
        reviewCase.setStatus(status);
        reviewCase.setMemoryBeforeJson("{\"id\":11,\"status\":\"active\",\"memoryType\":\"preference\",\"content\":\"prefers markdown\"}");
        reviewCase.setSourceContextJson("{\"sourceTraceId\":\"trace-11\"}");
        reviewCase.setPolicySnapshotJson("{\"policy_source\":\"advisor\"}");
        reviewCase.setCreatedAt(LocalDateTime.parse("2026-07-09T10:00:00"));
        reviewCase.setUpdatedAt(LocalDateTime.parse("2026-07-09T10:00:00"));
        return reviewCase;
    }
}
