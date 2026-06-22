package com.ainote.app.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RagFeedbackRequest {

    @NotBlank(message = "Query is required")
    @Size(max = 8000, message = "Query must be at most 8000 characters")
    private String query;

    @Size(max = 64, message = "Result note id must be at most 64 characters")
    private String resultNoteId;

    @DecimalMin(value = "0.0", message = "Similarity score must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Similarity score must be at most 1.0")
    private Double similarityScore;

    @Pattern(regexp = "[A-Z_]{1,40}", message = "Feedback type is invalid")
    private String feedbackType;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getResultNoteId() {
        return resultNoteId;
    }

    public void setResultNoteId(String resultNoteId) {
        this.resultNoteId = resultNoteId;
    }

    public Double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(Double similarityScore) {
        this.similarityScore = similarityScore;
    }

    public String getFeedbackType() {
        return feedbackType;
    }

    public void setFeedbackType(String feedbackType) {
        this.feedbackType = feedbackType;
    }
}
