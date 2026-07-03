package com.ainote.app.model.memory;

import java.util.List;

public record MemoryListResponse(
        List<MemoryResponse> items,
        String nextCursor
) {
}
