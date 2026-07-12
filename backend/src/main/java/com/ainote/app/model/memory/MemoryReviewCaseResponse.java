package com.ainote.app.model.memory;

import java.time.LocalDateTime;

public record MemoryReviewCaseResponse(
        Long id,
        Long memoryId,
        String userId,
        String feedbackType,
        String userComment,
        String expectedContent,
        String expectedMemoryType,
        Boolean expectedCaptureAllowed,
        String status,
        String reviewerId,
        String reviewerDecision,
        String reviewerComment,
        String replayCaseId,
        String replayCaseJson,
        String manifestJson,
        String memoryBeforeJson,
        String sourceContextJson,
        String policySnapshotJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime reviewedAt
) {
}
