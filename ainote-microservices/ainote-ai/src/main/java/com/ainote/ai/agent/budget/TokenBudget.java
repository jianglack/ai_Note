package com.ainote.ai.agent.budget;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class TokenBudget {

    private static final Logger log = LoggerFactory.getLogger(TokenBudget.class);

    @Value("${app.agent.max-tokens-per-request:50000}")
    private int maxTokensPerRequest;

    @Value("${app.agent.warn-tokens-threshold:40000}")
    private int warnThreshold;

    private static final ThreadLocal<AtomicInteger> CURRENT_TOKENS =
            ThreadLocal.withInitial(() -> new AtomicInteger(0));

    public void reset() {
        CURRENT_TOKENS.remove();
    }

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
