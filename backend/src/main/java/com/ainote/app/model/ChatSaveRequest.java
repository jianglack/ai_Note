package com.ainote.app.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public class ChatSaveRequest {

    @Size(max = 8000, message = "User message must be at most 8000 characters")
    private String userMessage;

    @Size(max = 20000, message = "AI reply must be at most 20000 characters")
    private String aiReply;

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getAiReply() {
        return aiReply;
    }

    public void setAiReply(String aiReply) {
        this.aiReply = aiReply;
    }

    @AssertTrue(message = "User message or AI reply is required")
    public boolean isMessagePresent() {
        return hasText(userMessage) || hasText(aiReply);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
