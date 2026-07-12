package com.ainote.app.model.memory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record MemoryExportResponse(
        List<MemoryResponse> items,
        String nextCursor,
        LocalDateTime exportedAt,
        int itemCount,
        boolean redacted,
        Map<String, Integer> redactionSummary,
        String schemaVersion
) {
}
