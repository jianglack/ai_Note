package com.ainote.app.model.memory;

import java.time.LocalDateTime;

public record MemoryResponse(
        Long id,
        String type,
        String memoryType,
        String category,
        String content,
        Double confidence,
        String source,
        String scope,
        String status,
        String sourceTraceId,
        String sourceMessageIds,
        String sourceToolCallId,
        String evidenceExcerpt,
        String metadataJson,
        LocalDateTime lastAccessedAt,
        Integer accessCount,
        Long supersedesId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
