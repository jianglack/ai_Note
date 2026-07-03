package com.ainote.app.model.memory;

public record MemoryUpdateRequest(
        String content,
        String status,
        String memoryType,
        String scope,
        Double confidence,
        String reason
) {
}
