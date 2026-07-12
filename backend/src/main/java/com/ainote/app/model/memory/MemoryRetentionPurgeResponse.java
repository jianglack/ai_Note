package com.ainote.app.model.memory;

import java.time.LocalDateTime;
import java.util.List;

public record MemoryRetentionPurgeResponse(
        int purgedCount,
        List<Long> purgedMemoryIds,
        LocalDateTime cutoff,
        LocalDateTime purgedAt,
        String status
) {
}
