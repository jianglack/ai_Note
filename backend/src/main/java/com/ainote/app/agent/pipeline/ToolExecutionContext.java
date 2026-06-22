package com.ainote.app.agent.pipeline;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 贯穿管道的执行上下文，记录完整执行信息。
 * 在管道各阶段逐步填充，最终用于审计日志记录。
 */
public class ToolExecutionContext {

    // ---- 创建时确定的不可变字段 ----
    private final String requestId;
    private final String userId;
    private final String toolName;
    private final String action;
    private final JsonNode params;
    private final long startTimeMs;

    // ---- 管道各阶段写入的可变字段 ----
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

    // ---- Getters ----

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

    // ---- Setters（管道各阶段调用） ----

    public void setGuardResult(GuardResult guardResult) { this.guardResult = guardResult; }
    public void setRawResult(String rawResult) { this.rawResult = rawResult; }
    public void setOutcome(ToolOutcome outcome) { this.outcome = outcome; }
    public void setSideEffect(ToolSideEffect sideEffect) { this.sideEffect = sideEffect; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
