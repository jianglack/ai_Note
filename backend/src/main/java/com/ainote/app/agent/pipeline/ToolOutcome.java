package com.ainote.app.agent.pipeline;

import java.util.Map;

/**
 * 结构化工具返回值，替代自由文本。
 * 所有工具执行结果统一为此结构，便于后续审计和分析。
 */
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

    // ---- 便捷工厂方法 ----

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

    /**
     * 序列化为工具返回字符串。
     * LangChain4j @Tool 方法必须返回 String，此方法将结构化结果转为可读格式。
     */
    public String toToolResponse() {
        return message;
    }
}
