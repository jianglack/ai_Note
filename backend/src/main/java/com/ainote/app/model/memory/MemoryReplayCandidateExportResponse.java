package com.ainote.app.model.memory;

import java.util.List;

public record MemoryReplayCandidateExportResponse(
        List<MemoryReviewCaseResponse> items
) {
}
