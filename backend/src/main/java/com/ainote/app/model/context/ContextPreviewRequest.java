package com.ainote.app.model.context;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ContextPreviewRequest(
        @NotBlank String query,
        List<String> noteIds
) {
}
