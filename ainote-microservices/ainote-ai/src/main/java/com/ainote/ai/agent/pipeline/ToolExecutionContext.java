package com.ainote.ai.agent.pipeline;

import com.fasterxml.jackson.databind.JsonNode;

public class ToolExecutionContext {

    private final String requestId;
    private final String userId;
    private final String toolName;
    private final String action;
    private final JsonNode params;
    private final long startTimeMs;

    private GuardResult guardResult;
    private String rawResult;
    private ToolOutcome outcome;
    private ToolSideEffect sideEffect;
    private long durationMs;

    public ToolExecutionContext(String requestId, String userId, String toolName,
                                String action, JsonNode params) {
        this.requestId = requestId;
        this.userId = userId;
        this.toolName = toolName;
        this.action = action;
        this.params = params;
        this.startTimeMs = System.currentTimeMillis();
    }

    public String getRequestId() { return requestId; }
    public String getUserId() { return userId; }
    public String getToolName() { return toolName; }
    public String getAction() { return action; }
    public JsonNode getParams() { return params; }
    public long getStartTimeMs() { return startTimeMs; }

    public GuardResult getGuardResult() { return guardResult; }
    public String getRawResult() { return rawResult; }
    public ToolOutcome getOutcome() { return outcome; }
    public ToolSideEffect getSideEffect() { return sideEffect; }
    public long getDurationMs() { return durationMs; }

    public void setGuardResult(GuardResult guardResult) { this.guardResult = guardResult; }
    public void setRawResult(String rawResult) { this.rawResult = rawResult; }
    public void setOutcome(ToolOutcome outcome) { this.outcome = outcome; }
    public void setSideEffect(ToolSideEffect sideEffect) { this.sideEffect = sideEffect; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
