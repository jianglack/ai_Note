package com.ainote.ai.agent.pipeline;

import java.util.Map;

public record ToolOutcome(
        Status status,
        String message,
        String entityType,
        String entityId,
        Map<String, Object> data
) {

    public enum Status {
        SUCCESS,
        FAILED,
        PENDING_CONFIRM
    }

    public static ToolOutcome success(String message) {
        return new ToolOutcome(Status.SUCCESS, message, null, null, null);
    }

    public static ToolOutcome success(String message, String entityType, String entityId) {
        return new ToolOutcome(Status.SUCCESS, message, entityType, entityId, null);
    }

    public static ToolOutcome success(String message, String entityType, String entityId,
                                      Map<String, Object> data) {
        return new ToolOutcome(Status.SUCCESS, message, entityType, entityId, data);
    }

    public static ToolOutcome failed(String message) {
        return new ToolOutcome(Status.FAILED, message, null, null, null);
    }

    public static ToolOutcome pendingConfirm(String message, Map<String, Object> confirmData) {
        return new ToolOutcome(Status.PENDING_CONFIRM, message, null, null, confirmData);
    }

    public String toToolResponse() {
        return message;
    }
}
