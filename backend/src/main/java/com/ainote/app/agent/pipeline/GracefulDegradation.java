package com.ainote.app.agent.pipeline;

import org.springframework.stereotype.Component;

/**
 * 优雅降级控制器。
 * <p>
 * 当 Agent 循环中工具调用次数接近上限时，通过 ThreadLocal 标记状态，
 * 在工具返回结果后追加系统提示，引导 LLM 优先完成最重要的操作并总结进展。
 * </p>
 * <p>
 * 由 AgentConfig 的 beforeToolExecution 回调在接近上限时调用 {@link #markApproachingLimit()}，
 * 由 ToolExecutionPipeline 在返回结果时调用 {@link #appendHintIfNeeded(String)}。
 * 每次 Agent 对话轮次结束后需调用 {@link #reset()} 清理。
 * </p>
 */
@Component
public class GracefulDegradation {

    private static final ThreadLocal<Boolean> APPROACHING_LIMIT =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final String LIMIT_HINT =
            "\n[系统提示：工具调用次数即将达到上限，请优先完成最重要的操作并总结进展]";

    /**
     * 标记当前轮次即将达到工具调用上限。
     */
    public static void markApproachingLimit() {
        APPROACHING_LIMIT.set(Boolean.TRUE);
    }

    /**
     * 重置标记。每次 Agent 对话轮次结束后调用。
     */
    public static void reset() {
        APPROACHING_LIMIT.remove();
    }

    /**
     * 如果已标记接近上限，在工具返回结果后追加提示。
     */
    public static String appendHintIfNeeded(String toolResult) {
        if (Boolean.TRUE.equals(APPROACHING_LIMIT.get())) {
            return toolResult + LIMIT_HINT;
        }
        return toolResult;
    }
}
