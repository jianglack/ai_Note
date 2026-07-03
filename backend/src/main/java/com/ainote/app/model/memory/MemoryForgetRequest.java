package com.ainote.app.model.memory;

import java.util.List;

public record MemoryForgetRequest(
        List<Long> memoryIds,
        String query,
        String reason
) {
}
