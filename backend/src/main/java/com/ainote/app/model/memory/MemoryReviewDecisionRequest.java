package com.ainote.app.model.memory;

public record MemoryReviewDecisionRequest(
        String decision,
        String reviewerComment,
        String correctedContent,
        String correctedMemoryType,
        Double correctedConfidence
) {
}
