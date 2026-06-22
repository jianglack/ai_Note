package com.ainote.app.agent.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 工具执行审计日志。
 * <p>
 * 每次 Agent 对话轮次内，通过 ThreadLocal 记录所有工具调用的完整执行链（transcript）。
 * 由 AgentService 在每次 chat() 开始前 {@link #resetTranscript()}，
 * 结束后可通过 {@link #getTranscript()} 获取本轮完整记录用于调试或返回前端。
 * </p>
 */
@Component
public class ToolAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(ToolAuditLogger.class);

    private static final ThreadLocal<List<ToolAuditEntry>> TRANSCRIPT =
            ThreadLocal.withInitial(ArrayList::new);

    /**
     * 记录一次工具执行。
     */
    public void log(ToolExecutionContext ctx) {
        ToolAuditEntry entry = new ToolAuditEntry(
                ctx.getRequestId(),
                ctx.getToolName(),
                ctx.getAction(),
                ctx.getParams().toString(),
                ctx.getOutcome() != null ? ctx.getOutcome().status().name() : "UNKNOWN",
                truncateResult(ctx.getRawResult()),
                ctx.getDurationMs(),
                ctx.getGuardResult() != null && ctx.getGuardResult().isBlocked(),
                ctx.getGuardResult() != null ? ctx.getGuardResult().getReason() : null,
                ctx.getSideEffect()
        );
        TRANSCRIPT.get().add(entry);

        if (entry.blocked()) {
            log.warn("Tool audit [BLOCKED]: {}:{} — reason: {} ({}ms)",
                    ctx.getToolName(), ctx.getAction(), entry.blockReason(), ctx.getDurationMs());
        } else {
            log.info("Tool audit: {}:{} → {} ({}ms)",
                    ctx.getToolName(), ctx.getAction(), entry.status(), ctx.getDurationMs());
        }
    }

    /**
     * 获取当前轮次的完整执行记录（不可变视图）。
     */
    public static List<ToolAuditEntry> getTranscript() {
        return Collections.unmodifiableList(TRANSCRIPT.get());
    }

    /**
     * 重置执行记录。每次 Agent 对话轮次开始前调用。
     */
    public static void resetTranscript() {
        TRANSCRIPT.remove();
    }

    /**
     * 截断过长的结果文本，避免审计日志占用过多内存。
     */
    private String truncateResult(String result) {
        if (result == null) return null;
        return result.length() > 500 ? result.substring(0, 500) + "...[truncated]" : result;
    }

    /**
     * 审计条目 — 记录单次工具调用的完整信息。
     */
    public record ToolAuditEntry(
            String requestId,
            String toolName,
            String action,
            String params,
            String status,
            String result,
            long durationMs,
            boolean blocked,
            String blockReason,
            ToolSideEffect sideEffect
    ) {}
}
