package com.ainote.app.model.memory;

public record MemoryFeedbackRequest(
        String feedbackType,
        String comment,
        String expectedContent,
        String expectedMemoryType,
        Boolean expectedCaptureAllowed
) {
}
