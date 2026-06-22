package com.ainote.ai.agent.pipeline;

import org.springframework.stereotype.Component;

@Component
public class GracefulDegradation {

    private static final ThreadLocal<Boolean> APPROACHING_LIMIT =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final String LIMIT_HINT =
            "\n[系统提示：工具调用次数即将达到上限，请优先完成最重要的操作并总结进展]";

    public static void markApproachingLimit() {
        APPROACHING_LIMIT.set(Boolean.TRUE);
    }

    public static void reset() {
        APPROACHING_LIMIT.remove();
    }

    public static String appendHintIfNeeded(String toolResult) {
        if (Boolean.TRUE.equals(APPROACHING_LIMIT.get())) {
            return toolResult + LIMIT_HINT;
        }
        return toolResult;
    }
}
