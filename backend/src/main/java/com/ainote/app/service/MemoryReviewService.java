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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
@Transactional
public class MemoryReviewService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final SemanticMemoryRepository semanticMemoryRepository;
    private final MemoryEventRepository memoryEventRepository;
    private final MemoryReviewCaseRepository memoryReviewCaseRepository;
    private final MemoryPrivacyService privacyService;
    private final MemoryMetricsService memoryMetricsService;

    public MemoryReviewService(SemanticMemoryRepository semanticMemoryRepository,
                               MemoryEventRepository memoryEventRepository,
                               MemoryReviewCaseRepository memoryReviewCaseRepository) {
        this(semanticMemoryRepository, memoryEventRepository, memoryReviewCaseRepository, new MemoryPrivacyService(),
                MemoryMetricsService.noop());
    }

    public MemoryReviewService(SemanticMemoryRepository semanticMemoryRepository,
                               MemoryEventRepository memoryEventRepository,
                               MemoryReviewCaseRepository memoryReviewCaseRepository,
                               MemoryPrivacyService privacyService) {
        this(semanticMemoryRepository, memoryEventRepository, memoryReviewCaseRepository, privacyService,
                MemoryMetricsService.noop());
    }

    @Autowired
    public MemoryReviewService(SemanticMemoryRepository semanticMemoryRepository,
                               MemoryEventRepository memoryEventRepository,
                               MemoryReviewCaseRepository memoryReviewCaseRepository,
                               MemoryPrivacyService privacyService,
                               MemoryMetricsService memoryMetricsService) {
        this.semanticMemoryRepository = semanticMemoryRepository;
        this.memoryEventRepository = memoryEventRepository;
        this.memoryReviewCaseRepository = memoryReviewCaseRepository;
        this.privacyService = privacyService == null ? new MemoryPrivacyService() : privacyService;
        this.memoryMetricsService = memoryMetricsService == null ? MemoryMetricsService.noop() : memoryMetricsService;
    }

    public MemoryReviewCaseResponse createFeedback(String userId,
                                                   Long memoryId,
                                                   MemoryFeedbackRequest request) {
        SemanticMemory memory = semanticMemoryRepository.findByIdAndUserId(memoryId, userId)
                .orElseThrow(() -> new NoSuchElementException("Memory not found: " + memoryId));

        MemoryReviewCase reviewCase = new MemoryReviewCase();
        reviewCase.setUserId(userId);
        reviewCase.setMemoryId(memoryId);
        reviewCase.setFeedbackType(normalizeFeedbackType(request.feedbackType()));
        reviewCase.setUserComment(blankToNull(privacyService.redact(request.comment())));
        reviewCase.setExpectedContent(blankToNull(privacyService.redact(request.expectedContent())));
        reviewCase.setExpectedMemoryType(normalizeOptional(request.expectedMemoryType()));
        reviewCase.setExpectedCaptureAllowed(request.expectedCaptureAllowed());
        reviewCase.setStatus("pending_review");
        reviewCase.setMemoryBeforeJson(memorySnapshot(memory));
        reviewCase.setSourceContextJson(sourceContextSnapshot(memory));
        reviewCase.setPolicySnapshotJson(policySnapshot(memory));

        MemoryReviewCase saved = memoryReviewCaseRepository.save(reviewCase);
        recordEvent(
                userId,
                memoryId,
                "FEEDBACK_REPORTED",
                "user",
                request.comment(),
                null,
                reviewSnapshot(saved),
                "memory-review-" + saved.getId());
        memoryMetricsService.recordFeedback(saved.getFeedbackType());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public MemoryReviewCaseListResponse listReviewCases(String status, Integer limit) {
        String normalizedStatus = status == null || status.isBlank()
                ? "pending_review"
                : normalizeStatus(status);
        return new MemoryReviewCaseListResponse(memoryReviewCaseRepository
                .findByStatusOrderByCreatedAtAsc(
                        normalizedStatus,
                        PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(this::toResponse)
                .toList());
    }

    public MemoryReviewCaseResponse decideReviewCase(Long reviewCaseId,
                                                     String reviewerId,
                                                     MemoryReviewDecisionRequest request) {
        MemoryReviewCase reviewCase = memoryReviewCaseRepository.findById(reviewCaseId)
                .orElseThrow(() -> new NoSuchElementException("Memory review case not found: " + reviewCaseId));

        String decision = normalizeDecision(request.decision());
        String beforeReview = reviewSnapshot(reviewCase);
        reviewCase.setReviewerId(reviewerId);
        reviewCase.setReviewerDecision(decision);
        reviewCase.setReviewerComment(blankToNull(request.reviewerComment()));
        reviewCase.setReviewedAt(LocalDateTime.now());

        switch (decision) {
            case "reject_feedback" -> {
                reviewCase.setStatus("rejected");
                MemoryReviewCase saved = memoryReviewCaseRepository.save(reviewCase);
                recordEvent(
                        saved.getUserId(),
                        saved.getMemoryId(),
                        "REVIEW_REJECTED",
                        "admin",
                        request.reviewerComment(),
                        beforeReview,
                        reviewSnapshot(saved),
                        "memory-review-" + saved.getId());
                return toResponse(saved);
            }
            case "disable_memory" -> {
                SemanticMemory memory = loadReviewedMemory(reviewCase);
                String beforeMemory = memorySnapshot(memory);
                memory.setStatus("disabled");
                SemanticMemory savedMemory = semanticMemoryRepository.save(memory);
                reviewCase.setStatus("actioned");
                MemoryReviewCase saved = memoryReviewCaseRepository.save(reviewCase);
                recordEvent(
                        saved.getUserId(),
                        saved.getMemoryId(),
                        "REVIEW_DISABLED",
                        "admin",
                        request.reviewerComment(),
                        beforeMemory,
                        memorySnapshot(savedMemory),
                        "memory-review-" + saved.getId());
                return toResponse(saved);
            }
            case "delete_memory" -> {
                SemanticMemory memory = loadReviewedMemory(reviewCase);
                String beforeMemory = memorySnapshot(memory);
                memory.setStatus("deleted");
                SemanticMemory savedMemory = semanticMemoryRepository.save(memory);
                reviewCase.setStatus("actioned");
                MemoryReviewCase saved = memoryReviewCaseRepository.save(reviewCase);
                recordEvent(
                        saved.getUserId(),
                        saved.getMemoryId(),
                        "REVIEW_DELETED",
                        "admin",
                        request.reviewerComment(),
                        beforeMemory,
                        memorySnapshot(savedMemory),
                        "memory-review-" + saved.getId());
                return toResponse(saved);
            }
            case "update_memory" -> {
                SemanticMemory memory = loadReviewedMemory(reviewCase);
                String beforeMemory = memorySnapshot(memory);
                if (request.correctedContent() != null && !request.correctedContent().isBlank()) {
                    assertSafeToStore(request.correctedContent());
                    memory.setContent(request.correctedContent());
                }
                if (request.correctedMemoryType() != null && !request.correctedMemoryType().isBlank()) {
                    memory.setMemoryType(normalizeOptional(request.correctedMemoryType()));
                }
                if (request.correctedConfidence() != null) {
                    memory.setConfidence(request.correctedConfidence());
                }
                SemanticMemory savedMemory = semanticMemoryRepository.save(memory);
                reviewCase.setStatus("actioned");
                MemoryReviewCase saved = memoryReviewCaseRepository.save(reviewCase);
                recordEvent(
                        saved.getUserId(),
                        saved.getMemoryId(),
                        "REVIEW_UPDATED",
                        "admin",
                        request.reviewerComment(),
                        beforeMemory,
                        memorySnapshot(savedMemory),
                        "memory-review-" + saved.getId());
                return toResponse(saved);
            }
            case "approve_replay" -> {
                reviewCase.setStatus("approved_for_replay");
                reviewCase.setReplayCaseId("review-" + reviewCase.getId());
                reviewCase.setReplayCaseJson(replayCaseJson(reviewCase));
                reviewCase.setManifestJson(manifestJson(reviewCase));
                MemoryReviewCase saved = memoryReviewCaseRepository.save(reviewCase);
                recordEvent(
                        saved.getUserId(),
                        saved.getMemoryId(),
                        "REVIEW_APPROVED_FOR_REPLAY",
                        "admin",
                        request.reviewerComment(),
                        beforeReview,
                        reviewSnapshot(saved),
                        "memory-review-" + saved.getId());
                return toResponse(saved);
            }
            case "mark_duplicate" -> {
                reviewCase.setStatus("duplicate");
                MemoryReviewCase saved = memoryReviewCaseRepository.save(reviewCase);
                recordEvent(
                        saved.getUserId(),
                        saved.getMemoryId(),
                        "REVIEW_ACTIONED",
                        "admin",
                        request.reviewerComment(),
                        beforeReview,
                        reviewSnapshot(saved),
                        "memory-review-" + saved.getId());
                return toResponse(saved);
            }
            default -> throw new IllegalArgumentException("Unsupported review decision: " + request.decision());
        }
    }

    @Transactional(readOnly = true)
    public MemoryReplayCandidateExportResponse exportApprovedReplayCandidates(Integer limit) {
        return new MemoryReplayCandidateExportResponse(memoryReviewCaseRepository
                .findByStatusOrderByReviewedAtDesc(
                        "approved_for_replay",
                        PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(this::toResponse)
                .toList());
    }

    private SemanticMemory loadReviewedMemory(MemoryReviewCase reviewCase) {
        return semanticMemoryRepository.findByIdAndUserId(reviewCase.getMemoryId(), reviewCase.getUserId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Memory not found for review case: " + reviewCase.getId()));
    }

    private void recordEvent(String userId,
                             Long memoryId,
                             String eventType,
                             String actor,
                             String reason,
                             String beforeJson,
                             String afterJson,
                             String traceId) {
        MemoryEvent event = new MemoryEvent();
        event.setUserId(userId);
        event.setMemoryId(memoryId);
        event.setEventType(eventType);
        event.setActor(actor);
        event.setReason(privacyService.redact(reason));
        event.setBeforeJson(beforeJson);
        event.setAfterJson(afterJson);
        event.setTraceId(traceId);
        memoryEventRepository.save(event);
    }

    private MemoryReviewCaseResponse toResponse(MemoryReviewCase reviewCase) {
        return new MemoryReviewCaseResponse(
                reviewCase.getId(),
                reviewCase.getMemoryId(),
                reviewCase.getUserId(),
                reviewCase.getFeedbackType(),
                reviewCase.getUserComment(),
                reviewCase.getExpectedContent(),
                reviewCase.getExpectedMemoryType(),
                reviewCase.getExpectedCaptureAllowed(),
                reviewCase.getStatus(),
                reviewCase.getReviewerId(),
                reviewCase.getReviewerDecision(),
                reviewCase.getReviewerComment(),
                reviewCase.getReplayCaseId(),
                reviewCase.getReplayCaseJson(),
                reviewCase.getManifestJson(),
                reviewCase.getMemoryBeforeJson(),
                reviewCase.getSourceContextJson(),
                reviewCase.getPolicySnapshotJson(),
                reviewCase.getCreatedAt(),
                reviewCase.getUpdatedAt(),
                reviewCase.getReviewedAt());
    }

    private String memorySnapshot(SemanticMemory memory) {
        return "{"
                + jsonField("id", memory.getId()) + ","
                + jsonField("status", memory.getStatus()) + ","
                + jsonField("memoryType", memory.getMemoryType()) + ","
                + jsonField("category", memory.getCategory()) + ","
                + jsonField("content", privacyService.redact(memory.getContent())) + ","
                + jsonField("confidence", memory.getConfidence())
                + "}";
    }

    private String sourceContextSnapshot(SemanticMemory memory) {
        return "{"
                + jsonField("source", memory.getSource()) + ","
                + jsonField("sourceTraceId", memory.getSourceTraceId()) + ","
                + jsonField("sourceMessageIds", memory.getSourceMessageIds()) + ","
                + jsonField("sourceToolCallId", memory.getSourceToolCallId()) + ","
                + jsonField("evidenceExcerpt", privacyService.redact(memory.getEvidenceExcerpt()))
                + "}";
    }

    private String policySnapshot(SemanticMemory memory) {
        return memory.getMetadataJson() == null || memory.getMetadataJson().isBlank()
                ? "{}"
                : privacyService.redact(memory.getMetadataJson());
    }

    private void assertSafeToStore(String content) {
        MemoryPrivacyService.MemoryPrivacyScanResult scan = privacyService.scan(content);
        if (!scan.safeToStore()) {
            throw new IllegalArgumentException("Memory content contains sensitive data: " + scan.counts().keySet());
        }
    }

    private String reviewSnapshot(MemoryReviewCase reviewCase) {
        return "{"
                + jsonField("id", reviewCase.getId()) + ","
                + jsonField("memoryId", reviewCase.getMemoryId()) + ","
                + jsonField("status", reviewCase.getStatus()) + ","
                + jsonField("feedbackType", reviewCase.getFeedbackType()) + ","
                + jsonField("reviewerDecision", reviewCase.getReviewerDecision()) + ","
                + jsonField("replayCaseId", reviewCase.getReplayCaseId())
                + "}";
    }

    private String replayCaseJson(MemoryReviewCase reviewCase) {
        Boolean captureAllowed = reviewCase.getExpectedCaptureAllowed();
        if (captureAllowed == null) {
            captureAllowed = !("wrong_memory".equals(reviewCase.getFeedbackType())
                    || "should_not_remember".equals(reviewCase.getFeedbackType()));
        }
        return "{"
                + jsonField("id", "review-" + reviewCase.getId()) + ","
                + jsonField("source", "human_review_feedback") + ","
                + jsonField("feedbackType", reviewCase.getFeedbackType()) + ","
                + jsonField("reviewerDecision", reviewCase.getReviewerDecision()) + ","
                + jsonRawField("memoryBefore", reviewCase.getMemoryBeforeJson()) + ","
                + jsonRawField("sourceContext", reviewCase.getSourceContextJson()) + ","
                + jsonRawField("policySnapshot", reviewCase.getPolicySnapshotJson()) + ","
                + "\"expected\":{"
                + jsonField("captureAllowed", captureAllowed) + ","
                + jsonField("memoryType", reviewCase.getExpectedMemoryType()) + ","
                + jsonField("content", reviewCase.getExpectedContent())
                + "}"
                + "}";
    }

    private String manifestJson(MemoryReviewCase reviewCase) {
        return "{"
                + jsonField("reviewCaseId", reviewCase.getId()) + ","
                + jsonField("reviewerId", reviewCase.getReviewerId()) + ","
                + jsonField("reviewedAt", reviewCase.getReviewedAt() == null
                        ? null
                        : reviewCase.getReviewedAt().toString()) + ","
                + jsonField("source", "memory_review_cases") + ","
                + jsonField("provenance", "human_review_feedback")
                + "}";
    }

    private String normalizeFeedbackType(String feedbackType) {
        String normalized = normalizeRequired(feedbackType, "feedbackType");
        return switch (normalized) {
            case "wrong_memory", "should_not_remember", "type_wrong", "outdated", "missing_context", "other" ->
                    normalized;
            default -> throw new IllegalArgumentException("Unsupported memory feedback type: " + feedbackType);
        };
    }

    private String normalizeDecision(String decision) {
        String normalized = normalizeRequired(decision, "decision");
        return switch (normalized) {
            case "reject_feedback", "disable_memory", "delete_memory", "update_memory", "approve_replay",
                    "mark_duplicate" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported review decision: " + decision);
        };
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeRequired(status, "status");
        return switch (normalized) {
            case "pending_review", "rejected", "actioned", "approved_for_replay", "duplicate" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported review status: " + status);
        };
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + fieldName);
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAX_LIMIT, limit));
    }

    private String jsonField(String name, String value) {
        return quote(name) + ":" + quote(value);
    }

    private String jsonField(String name, Long value) {
        return quote(name) + ":" + (value == null ? "null" : value);
    }

    private String jsonField(String name, Double value) {
        return quote(name) + ":" + (value == null ? "null" : value);
    }

    private String jsonField(String name, Boolean value) {
        return quote(name) + ":" + (value == null ? "null" : value);
    }

    private String jsonRawField(String name, String rawJson) {
        return quote(name) + ":" + (rawJson == null || rawJson.isBlank() ? "{}" : rawJson);
    }

    private String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                + "\"";
    }
}
