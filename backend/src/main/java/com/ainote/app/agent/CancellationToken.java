package com.ainote.app.agent;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 请求取消令牌。
 * <p>
 * 每次 Agent 调用创建一个实例，通过 requestId 注册到 AgentService。
 * SSE 断开、超时或用户主动取消时调用 {@link #cancel()}。
 * ToolExecutionPipeline 在每次工具执行前检查 {@link #isCancelled()}。
 * </p>
 */
public class CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * 请求取消。
     */
    public void cancel() {
        cancelled.set(true);
    }

    /**
     * 检查是否已取消。
     */
    public boolean isCancelled() {
        return cancelled.get();
    }
}
