package com.ainote.app.service.chat;

public class ChatStrategyException extends RuntimeException {

    private final String errorCode;
    private final String originalContent;

    public ChatStrategyException(String errorCode, String message) {
        this(errorCode, message, null);
    }

    public ChatStrategyException(String errorCode, String message, String originalContent) {
        super(message);
        this.errorCode = errorCode;
        this.originalContent = originalContent;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getOriginalContent() {
        return originalContent;
    }
}
