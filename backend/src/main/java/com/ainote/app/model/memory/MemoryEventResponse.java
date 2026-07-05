package com.ainote.app.model.memory;

import java.time.LocalDateTime;

public record MemoryEventResponse(
        Long id,
        Long memoryId,
        String eventType,
        String actor,
        String reason,
        String beforeJson,
        String afterJson,
        String traceId,
        LocalDateTime createdAt
) {
}
