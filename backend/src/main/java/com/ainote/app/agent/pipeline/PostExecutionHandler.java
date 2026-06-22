package com.ainote.app.agent.pipeline;

import com.ainote.app.agent.pending.PendingActionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 后置执行处理器：在工具实际执行完成后进行三项处理。
 * <ol>
 *   <li>计算执行耗时</li>
 *   <li>解析原始返回为结构化 {@link ToolOutcome}</li>
 *   <li>提取写操作的副作用记录 {@link ToolSideEffect}</li>
 *   <li>通过 {@link ToolAuditLogger} 记录审计日志</li>
 * </ol>
 */
@Component
public class PostExecutionHandler {

    private static final Logger log = LoggerFactory.getLogger(PostExecutionHandler.class);

    private final ToolAuditLogger auditLogger;
    private final com.ainote.app.service.AgentMetricsService metricsService;
    private final PendingActionRegistry pendingActionRegistry;

    /**
     * 匹配返回文本中的实体 ID（如 "ID: abc-123"）
     */
    private static final Pattern ENTITY_ID_PATTERN =
            Pattern.compile("ID:\\s*([a-zA-Z0-9-]+)");

    /**
     * 失败指示关键词
     */
    private static final Set<String> FAILURE_INDICATORS = Set.of(
            "失败", "错误", "缺少", "未找到", "不存在", "无法", "error", "failed"
    );

    /**
     * 写操作集合
     */
    private static final Set<String> MUTATING_ACTIONS = Set.of(
            "create", "update", "delete", "confirmDelete",
            "permanentDelete", "confirmPermanentDelete",
            "move", "copy", "merge", "emptyTrash", "confirmEmptyTrash",
            "addTag", "removeTag", "restore",
            "createBatch", "rename"
    );

    public PostExecutionHandler(ToolAuditLogger auditLogger,
                               com.ainote.app.service.AgentMetricsService metricsService,
                               PendingActionRegistry pendingActionRegistry) {
        this.auditLogger = auditLogger;
        this.metricsService = metricsService;
        this.pendingActionRegistry = pendingActionRegistry;
    }

    /**
     * 处理工具执行结果。
     */
    public void handle(ToolExecutionContext ctx) {
        ctx.setDurationMs(System.currentTimeMillis() - ctx.getStartTimeMs());

        // 1. 解析结构化结果
        ToolOutcome outcome = parseOutcome(ctx.getRawResult(), ctx.getToolName());
        ctx.setOutcome(outcome);

        // 2. 记录写操作副作用
        if (outcome.status() == ToolOutcome.Status.SUCCESS && isMutating(ctx.getAction())) {
            ToolSideEffect effect = buildSideEffect(ctx);
            ctx.setSideEffect(effect);
        }

        // 3. 指标聚合
        boolean isSuccess = outcome.status() == ToolOutcome.Status.SUCCESS
                || outcome.status() == ToolOutcome.Status.PENDING_CONFIRM;
        metricsService.recordToolCall(ctx.getToolName(), ctx.getAction(), isSuccess, ctx.getDurationMs());

        // 4. 审计日志
        auditLogger.log(ctx);
    }

    /**
     * 将原始返回字符串解析为结构化 ToolOutcome。
     */
    private ToolOutcome parseOutcome(String rawResult, String toolName) {
        if (rawResult == null) {
            return ToolOutcome.failed("工具未返回结果");
        }

        // 检查是否为 PENDING_ACTION（确认请求）
        if (rawResult.contains("PENDING_ACTION:")) {
            if (pendingActionRegistry.extractActions(rawResult).isEmpty()) {
                return ToolOutcome.failed("Invalid PENDING_ACTION");
            }
            return ToolOutcome.pendingConfirm(rawResult, null);
        }

        // 检查是否为失败
        String lowerResult = rawResult.toLowerCase();
        for (String indicator : FAILURE_INDICATORS) {
            if (lowerResult.contains(indicator) && !lowerResult.contains("✅")) {
                // 含失败关键词且不含成功标记 → 判为失败
                // 但对搜索类结果放宽（"未找到" 是正常的空结果）
                if (indicator.equals("未找到") && (lowerResult.contains("相关") || lowerResult.contains("笔记"))) {
                    return ToolOutcome.success(rawResult);
                }
                return ToolOutcome.failed(rawResult);
            }
        }

        // 默认成功，尝试提取实体信息
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

    /**
     * 从 action 推断操作类型。
     */
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

    /**
     * 从 toolName 推断实体类型。
     */
    private String inferEntityType(String toolName) {
        return switch (toolName) {
            case "noteAction" -> "NOTE";
            case "scheduleAction" -> "SCHEDULE";
            case "folderAction" -> "FOLDER";
            case "knowledgeAction" -> "KNOWLEDGE";
            case "insightAction" -> "INSIGHT";
            default -> null;
        };
    }

    /**
     * 从结果文本中提取实体 ID。
     */
    private String extractEntityId(String rawResult) {
        if (rawResult == null) return null;
        Matcher matcher = ENTITY_ID_PATTERN.matcher(rawResult);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 判断操作是否可回滚。
     * 永久删除、合并、清空回收站等不可逆操作返回 false。
     */
    private boolean isReversible(String action) {
        return !Set.of("merge", "confirmPermanentDelete", "confirmEmptyTrash",
                "permanentDelete").contains(action);
    }

    private boolean isMutating(String action) {
        return MUTATING_ACTIONS.contains(action);
    }
}
