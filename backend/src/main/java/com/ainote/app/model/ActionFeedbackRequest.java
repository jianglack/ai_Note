package com.ainote.app.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ActionFeedbackRequest {

    @NotBlank(message = "Action JSON is required")
    @Size(max = 20000, message = "Action JSON must be at most 20000 characters")
    private String actionJson;

    @NotNull(message = "Confirmed decision is required")
    private Boolean confirmed;

    @Size(max = 2000, message = "Feedback must be at most 2000 characters")
    private String feedback;

    public String getActionJson() {
        return actionJson;
    }

    public void setActionJson(String actionJson) {
        this.actionJson = actionJson;
    }

    public Boolean getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(Boolean confirmed) {
        this.confirmed = confirmed;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }
}
