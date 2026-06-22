package com.ainote.ai.agent.pipeline;

import com.ainote.ai.agent.pending.PendingActionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PostExecutionHandler {

    private static final Logger log = LoggerFactory.getLogger(PostExecutionHandler.class);

    private final ToolAuditLogger auditLogger;
    private final PendingActionRegistry pendingActionRegistry;

    private static final Pattern ENTITY_ID_PATTERN =
            Pattern.compile("ID:\\s*([a-zA-Z0-9-]+)");

    private static final Set<String> FAILURE_INDICATORS = Set.of(
            "失败", "错误", "缺少", "未找到", "不存在", "无法", "error", "failed"
    );

    private static final Set<String> MUTATING_ACTIONS = Set.of(
            "create", "update", "delete", "confirmDelete",
            "permanentDelete", "confirmPermanentDelete",
            "move", "copy", "merge", "emptyTrash", "confirmEmptyTrash",
            "addTag", "removeTag", "restore",
            "createBatch", "rename"
    );

    public PostExecutionHandler(ToolAuditLogger auditLogger, PendingActionRegistry pendingActionRegistry) {
        this.auditLogger = auditLogger;
        this.pendingActionRegistry = pendingActionRegistry;
    }

    public void handle(ToolExecutionContext ctx) {
        ctx.setDurationMs(System.currentTimeMillis() - ctx.getStartTimeMs());

        ToolOutcome outcome = parseOutcome(ctx.getRawResult(), ctx.getToolName());
        ctx.setOutcome(outcome);

        if (outcome.status() == ToolOutcome.Status.SUCCESS && isMutating(ctx.getAction())) {
            ToolSideEffect effect = buildSideEffect(ctx);
            ctx.setSideEffect(effect);
        }

        auditLogger.log(ctx);
    }

    private ToolOutcome parseOutcome(String rawResult, String toolName) {
        if (rawResult == null) {
            return ToolOutcome.failed("工具未返回结果");
        }

        if (rawResult.contains("PENDING_ACTION:")) {
            List<Map<String, Object>> actions = pendingActionRegistry.extractActions(rawResult);
            if (actions.isEmpty()) {
                return ToolOutcome.failed("Invalid PENDING_ACTION");
            }
            return ToolOutcome.pendingConfirm(rawResult, actions.get(0));
        }

        String lowerResult = rawResult.toLowerCase();
        for (String indicator : FAILURE_INDICATORS) {
            if (lowerResult.contains(indicator) && !lowerResult.contains("✅")) {
                if (indicator.equals("未找到") && (lowerResult.contains("相关") || lowerResult.contains("笔记"))) {
                    return ToolOutcome.success(rawResult);
                }
                return ToolOutcome.failed(rawResult);
            }
        }

        String entityType = inferEntityType(toolName);
        String entityId = extractEntityId(rawResult);
        return ToolOutcome.success(rawResult, entityType, entityId);
    }

    private ToolSideEffect buildSideEffect(ToolExecutionContext ctx) {
        String actionType = inferActionType(ctx.getAction());
        String entityType = inferEntityType(ctx.getToolName());
        String entityId = extractEntityId(ctx.getRawResult());
        boolean reversible = isReversible(ctx.getAction());
        return new ToolSideEffect(actionType, entityType, entityId, null, reversible);
    }

    private String inferActionType(String action) {
        return switch (action) {
            case "create", "createBatch" -> "CREATE";
            case "update", "rename", "addTag", "removeTag" -> "UPDATE";
            case "delete", "confirmDelete", "permanentDelete",
                 "confirmPermanentDelete", "emptyTrash", "confirmEmptyTrash" -> "DELETE";
            case "move" -> "MOVE";
            case "copy" -> "COPY";
            case "merge" -> "MERGE";
            case "restore" -> "RESTORE";
            default -> "UNKNOWN";
        };
    }

    private String inferEntityType(String toolName) {
        return switch (toolName) {
            case "noteAction" -> "NOTE";
            case "scheduleAction" -> "SCHEDULE";
            case "folderAction" -> "FOLDER";
            default -> null;
        };
    }

    private String extractEntityId(String rawResult) {
        if (rawResult == null) return null;
        Matcher matcher = ENTITY_ID_PATTERN.matcher(rawResult);
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean isReversible(String action) {
        return !Set.of("merge", "confirmPermanentDelete", "confirmEmptyTrash",
                "permanentDelete").contains(action);
    }

    private boolean isMutating(String action) {
        return MUTATING_ACTIONS.contains(action);
    }
}
