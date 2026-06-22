package com.ainote.app.agent.pipeline;

/**
 * 副作用记录，用于审计追踪和潜在的回滚操作。
 * 记录每次写操作的实体变更信息。
 */
public record ToolSideEffect(
        String action,          // CREATE / UPDATE / DELETE / MOVE / MERGE / COPY / RESTORE
        String entityType,      // NOTE / SCHEDULE / FOLDER
        String entityId,        // 受影响实体 ID
        String previousState,   // JSON 格式的修改前快照（UPDATE 时记录旧值），可为 null
        boolean reversible      // 是否可自动回滚
) {}
