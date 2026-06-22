package com.ainote.app.agent.pipeline;

import com.ainote.app.agent.tools.ToolLoopDetector;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 前置执行守卫：在工具实际执行前进行三重检查。
 * <ol>
 *   <li>参数校验 — 根据 toolName + action 检查必填字段</li>
 *   <li>循环检测 — 复用 ToolLoopDetector，防止 Agent 反复调用同一操作</li>
 *   <li>幂等检测 — 写操作 5 分钟内按参数摘要去重</li>
 * </ol>
 */
@Component
public class PreExecutionGuard {

    private static final Logger log = LoggerFactory.getLogger(PreExecutionGuard.class);

    private final ToolLoopDetector loopDetector;
    private final IdempotencyStore idempotencyStore;

    /**
     * 写操作集合 — 这些操作会被幂等检测覆盖
     */
    private static final Set<String> MUTATING_ACTIONS = Set.of(
            "create", "update", "delete", "confirmDelete",
            "permanentDelete", "confirmPermanentDelete",
            "move", "copy", "merge", "emptyTrash", "confirmEmptyTrash",
            "addTag", "removeTag", "restore",
            "createBatch", "rename"
    );

    /**
     * 各工具各 action 的必填参数映射。
     * key = "toolName:action"，value = 必填参数名数组
     */
    private static final Map<String, String[]> REQUIRED_PARAMS = Map.ofEntries(
            // ---- NoteActionTool ----
            Map.entry("noteAction:update", new String[]{"noteId"}),
            Map.entry("noteAction:delete", new String[]{"noteId"}),
            Map.entry("noteAction:confirmDelete", new String[]{"noteId"}),
            Map.entry("noteAction:permanentDelete", new String[]{"noteId"}),
            Map.entry("noteAction:confirmPermanentDelete", new String[]{"noteId"}),
            Map.entry("noteAction:addTag", new String[]{"noteId", "tagName"}),
            Map.entry("noteAction:removeTag", new String[]{"noteId", "tagName"}),
            Map.entry("noteAction:restore", new String[]{"noteId"}),
            Map.entry("noteAction:move", new String[]{"noteId"}),
            Map.entry("noteAction:copy", new String[]{"noteId"}),
            Map.entry("noteAction:merge", new String[]{"noteIds"}),
            Map.entry("noteAction:create", new String[]{"title"}),
            Map.entry("noteAction:suggestTags", new String[]{"noteId"}),

            // ---- ScheduleActionTool ----
            Map.entry("scheduleAction:create", new String[]{"title", "startTime"}),
            Map.entry("scheduleAction:createBatch", new String[]{"schedules"}),
            Map.entry("scheduleAction:delete", new String[]{"scheduleId"}),
            Map.entry("scheduleAction:confirmDelete", new String[]{"scheduleId"}),
            Map.entry("scheduleAction:extractFromNote", new String[]{"noteId"}),

            // ---- FolderActionTool ----
            Map.entry("folderAction:create", new String[]{"name"}),
            Map.entry("folderAction:rename", new String[]{"folderId", "name"}),
            Map.entry("folderAction:delete", new String[]{"folderId"}),
            Map.entry("folderAction:confirmDelete", new String[]{"folderId"}),

            // ---- KnowledgeActionTool ----
            Map.entry("knowledgeAction:findRelated", new String[]{"noteId"}),
            Map.entry("knowledgeAction:conceptSearch", new String[]{"concepts"}),
            Map.entry("knowledgeAction:noteConcepts", new String[]{"noteId"}),

            // ---- InsightActionTool ----
            // statistics, analyze, duplicates, timeline 无必填参数
            Map.entry("insightAction:timeline", new String[]{})  // days 是可选的
    );

    public PreExecutionGuard(ToolLoopDetector loopDetector, IdempotencyStore idempotencyStore) {
        this.loopDetector = loopDetector;
        this.idempotencyStore = idempotencyStore;
    }

    /**
     * 执行前置检查，返回守卫结果。
     */
    public GuardResult check(ToolExecutionContext ctx) {
        // 1. 参数校验
        String missing = validateRequiredParams(ctx.getToolName(), ctx.getAction(), ctx.getParams());
        if (missing != null) {
            log.warn("PreGuard blocked {}.{}: missing param '{}'", ctx.getToolName(), ctx.getAction(), missing);
            return GuardResult.blocked("缺少必填参数: " + missing);
        }

        // 2. 循环检测 — 用参数的 MD5 摘要做签名
        String paramsDigest = DigestUtils.md5Hex(ctx.getParams().toString());
        if (loopDetector.recordAndCheck(ctx.getToolName(), ctx.getAction(), paramsDigest)) {
            log.warn("PreGuard blocked {}.{}: loop detected", ctx.getToolName(), ctx.getAction());
            return GuardResult.blocked("检测到重复调用循环，已中止。请换一种方式描述您的需求。");
        }

        // 3. 幂等检测 — 仅对写操作生效
        if (isMutatingAction(ctx.getAction())) {
            String idempotencyKey = ctx.getUserId() + ":" + ctx.getToolName() + ":"
                    + ctx.getAction() + ":" + paramsDigest;
            if (idempotencyStore.isDuplicate(idempotencyKey)) {
                log.info("PreGuard blocked {}.{}: idempotent duplicate", ctx.getToolName(), ctx.getAction());
                return GuardResult.blocked("该操作刚刚已执行过，如需重复执行请稍后再试");
            }
        }

        return GuardResult.passed();
    }

    private String validateRequiredParams(String toolName, String action, JsonNode params) {
        String key = toolName + ":" + action;
        String[] required = REQUIRED_PARAMS.get(key);
        if (required == null) {
            return null; // 未注册的 action 不做校验（如 listAll, list, search 等）
        }
        for (String param : required) {
            if (!params.has(param) || params.get(param).isNull()) {
                return param;
            }
            // 对字符串类型进一步检查非空
            if (params.get(param).isTextual() && params.get(param).asText().isBlank()) {
                return param;
            }
        }
        return null;
    }

    private boolean isMutatingAction(String action) {
        return MUTATING_ACTIONS.contains(action);
    }
}
