package com.ainote.app.model.memory;

import java.time.LocalDateTime;
import java.util.List;

public record MemoryDeleteProofResponse(
        String requestId,
        String userId,
        List<Long> deletedMemoryIds,
        int deletedCount,
        List<String> contentHashes,
        LocalDateTime requestedAt,
        LocalDateTime completedAt,
        String status,
        String proofJson
) {
}
