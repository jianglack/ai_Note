package com.ainote.app.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EvalItemRequest {

    @NotBlank(message = "Question is required")
    @Size(max = 20000, message = "Question must be at most 20000 characters")
    private String question;

    @Size(max = 20000, message = "Expected answer must be at most 20000 characters")
    private String expectedAnswer;

    @Size(max = 200000, message = "Expected tool calls payload is too large")
    private String expectedToolCalls;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getExpectedAnswer() {
        return expectedAnswer;
    }

    public void setExpectedAnswer(String expectedAnswer) {
        this.expectedAnswer = expectedAnswer;
    }

    public String getExpectedToolCalls() {
        return expectedToolCalls;
    }

    public void setExpectedToolCalls(String expectedToolCalls) {
        this.expectedToolCalls = expectedToolCalls;
    }
}
