package com.ainote.app.model.memory;

import java.util.List;

public record MemoryEventListResponse(
        List<MemoryEventResponse> items
) {
}
