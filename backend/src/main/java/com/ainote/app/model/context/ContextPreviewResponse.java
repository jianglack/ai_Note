package com.ainote.app.model.context;

import java.util.List;

public record ContextPreviewResponse(
        String query,
        List<String> noteIds,
        int selectedNoteCount,
        String intent,
        int contextChars,
        int estimatedTokens,
        String finalContext,
        List<Section> sections,
        List<FlowStep> flow
) {
    public record Section(
            String type,
            String label,
            boolean included,
            int estimatedTokens,
            String content
    ) {
    }

    public record FlowStep(
            int order,
            String title,
            String detail,
            String status
    ) {
    }
}
