package com.ainote.app.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class AdminRagEvaluationRequest {

    @Min(value = 1, message = "k must be at least 1")
    @Max(value = 50, message = "k must be at most 50")
    private Integer k;

    @DecimalMin(value = "0.0", message = "minScore must be at least 0.0")
    @DecimalMax(value = "1.0", message = "minScore must be at most 1.0")
    private Double minScore;

    @NotEmpty(message = "Evaluation cases are required")
    @Size(max = 100, message = "At most 100 evaluation cases are allowed")
    private List<@Valid EvalCaseRequest> cases;

    public Integer getK() {
        return k;
    }

    public void setK(Integer k) {
        this.k = k;
    }

    public Double getMinScore() {
        return minScore;
    }

    public void setMinScore(Double minScore) {
        this.minScore = minScore;
    }

    public List<EvalCaseRequest> getCases() {
        return cases;
    }

    public void setCases(List<EvalCaseRequest> cases) {
        this.cases = cases;
    }

    public static class EvalCaseRequest {
        @NotBlank(message = "Query is required")
        @Size(max = 8000, message = "Query must be at most 8000 characters")
        private String query;

        @Size(max = 100, message = "At most 100 expected note ids are allowed")
        private List<@NotBlank(message = "Expected note id must not be blank") @Size(max = 64, message = "Expected note id must be at most 64 characters") String> expectedNoteIds;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public List<String> getExpectedNoteIds() {
            return expectedNoteIds;
        }

        public void setExpectedNoteIds(List<String> expectedNoteIds) {
            this.expectedNoteIds = expectedNoteIds;
        }
    }
}
