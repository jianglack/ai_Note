package com.ainote.app.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具调用循环检测器
 * 追踪单次 Agent 对话轮次中的工具调用签名，
 * 当连续出现相同签名超过阈值时中断循环。
 *
 * 生命周期：由 AgentService 在每次 chat() 调用前 reset()，调用后 reset()。
 */
@Component
public class ToolLoopDetector {

    private static final Logger log = LoggerFactory.getLogger(ToolLoopDetector.class);

    private final int maxRepeatedCalls;

    private static final ThreadLocal<List<String>> CALL_SIGNATURES = ThreadLocal.withInitial(ArrayList::new);

    public ToolLoopDetector(@Value("${app.agent.tool-loop-max-repeated:5}") int maxRepeatedCalls) {
        this.maxRepeatedCalls = maxRepeatedCalls;
    }

    public void reset() {
        CALL_SIGNATURES.remove();
    }

    /**
     * 记录工具调用签名并检测循环。
     *
     * @param toolName 工具名称
     * @param action   操作类型
     * @param params   参数摘要（如 noteId）
     * @return true 如果检测到循环，调用方应中止执行
     */
    public boolean recordAndCheck(String toolName, String action, String params) {
        String signature = toolName + ":" + action + ":" + params;
        List<String> signatures = CALL_SIGNATURES.get();
        signatures.add(signature);

        long count = signatures.stream().filter(s -> s.equals(signature)).count();
        if (count > maxRepeatedCalls) {
            log.warn("Tool loop detected: {} called {} times (limit: {}) in this turn", signature, count, maxRepeatedCalls);
            return true;
        }
        return false;
    }
}
