package com.ainote.ai.agent.pipeline;

public record ToolSideEffect(
        String action,
        String entityType,
        String entityId,
        String previousState,
        boolean reversible
) {}
