package com.ainote.app.agent.budget;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Token 预算管理器。
 * <p>
 * 追踪单次 Agent 请求中累计消耗的 token 数量，
 * 当接近或超过预算时发出警告，引导 LLM 及时收尾。
 * </p>
 * <p>
 * 生命周期：由 AgentService 在每次 chat() 开始前 {@link #reset()}，
 * 由 {@link com.ainote.app.observability.AgentTraceListener} 在每次 LLM 调用返回时 {@link #addTokens(int)} 累计。
 * </p>
 */
@Component
public class TokenBudget {

    private static final Logger log = LoggerFactory.getLogger(TokenBudget.class);

    @Value("${app.agent.max-tokens-per-request:50000}")
    private int maxTokensPerRequest;

    @Value("${app.agent.warn-tokens-threshold:40000}")
    private int warnThreshold;

    private static final ThreadLocal<AtomicInteger> CURRENT_TOKENS =
            ThreadLocal.withInitial(() -> new AtomicInteger(0));

    /**
     * 重置预算计数器。每次 Agent 对话轮次开始前调用。
     */
    public void reset() {
        CURRENT_TOKENS.remove();
    }

    /**
     * 累加 token 消耗。
     */
    public void addTokens(int count) {
        int total = CURRENT_TOKENS.get().addAndGet(count);
        if (total >= maxTokensPerRequest) {
            log.error("Token budget EXCEEDED: {}/{}", total, maxTokensPerRequest);
        } else if (total >= warnThreshold) {
            log.warn("Token budget approaching limit: {}/{}", total, maxTokensPerRequest);
        }
    }

    public int getCurrentTokens() {
        return CURRENT_TOKENS.get().get();
    }

    public boolean isApproachingLimit() {
        return getCurrentTokens() >= warnThreshold;
    }

    public boolean isExceeded() {
        return getCurrentTokens() >= maxTokensPerRequest;
    }

    /**
     * 返回需要追加到 system prompt 或工具结果中的预算提示。
     * 当剩余 token 不足 5000 时返回提示，否则返回空串。
     */
    public String getBudgetHint() {
        int remaining = maxTokensPerRequest - getCurrentTokens();
        if (remaining < 5000 && remaining > 0) {
            return "\n【注意】Token 预算即将用完（剩余约 " + remaining + "），请立即总结当前进展并回复用户。";
        }
        if (remaining <= 0) {
            return "\n【警告】Token 预算已超出，请立即停止工具调用并总结回复用户。";
        }
        return "";
    }
}
