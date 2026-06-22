package com.ainote.app.model;

import com.ainote.app.entity.AgentTrace;

import java.time.LocalDateTime;

public record AgentTraceResponse(
        String id,
        String traceId,
        String inputText,
        String outputText,
        String toolsCalled,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Integer latencyMs,
        String model,
        String status,
        String errorMessage,
        LocalDateTime createdAt
) {
    public static AgentTraceResponse from(AgentTrace trace) {
        return new AgentTraceResponse(
                trace.getId(),
                trace.getTraceId(),
                trace.getInputText(),
                trace.getOutputText(),
                trace.getToolsCalled(),
                trace.getInputTokens(),
                trace.getOutputTokens(),
                trace.getTotalTokens(),
                trace.getLatencyMs(),
                trace.getModel(),
                trace.getStatus(),
                trace.getErrorMessage(),
                trace.getCreatedAt()
        );
    }
}
