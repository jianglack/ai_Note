package com.ainote.ai.agent.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class ToolAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(ToolAuditLogger.class);

    private static final ThreadLocal<List<ToolAuditEntry>> TRANSCRIPT =
            ThreadLocal.withInitial(ArrayList::new);

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

    public static List<ToolAuditEntry> getTranscript() {
        return Collections.unmodifiableList(TRANSCRIPT.get());
    }

    public static void resetTranscript() {
        TRANSCRIPT.remove();
    }

    private String truncateResult(String result) {
        if (result == null) return null;
        return result.length() > 500 ? result.substring(0, 500) + "...[truncated]" : result;
    }

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
