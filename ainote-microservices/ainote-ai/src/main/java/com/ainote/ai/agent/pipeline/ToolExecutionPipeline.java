package com.ainote.ai.agent.pipeline;

import com.ainote.ai.agent.CancellationToken;
import com.ainote.ai.agent.budget.TokenBudget;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

@Component
public class ToolExecutionPipeline {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionPipeline.class);

    private static final ThreadLocal<CancellationToken> CURRENT_CANCEL_TOKEN = new ThreadLocal<>();

    private final PreExecutionGuard preGuard;
    private final PostExecutionHandler postHandler;
    private final TokenBudget tokenBudget;

    public ToolExecutionPipeline(PreExecutionGuard preGuard, PostExecutionHandler postHandler,
                                  TokenBudget tokenBudget) {
        this.preGuard = preGuard;
        this.postHandler = postHandler;
        this.tokenBudget = tokenBudget;
    }

    public static void setCancelToken(CancellationToken token) {
        CURRENT_CANCEL_TOKEN.set(token);
    }

    public static void clearCancelToken() {
        CURRENT_CANCEL_TOKEN.remove();
    }

    public String execute(String userId, String toolName, String action,
                          JsonNode params, Supplier<String> toolInvoker) {
        ToolExecutionContext ctx = new ToolExecutionContext(
                UUID.randomUUID().toString(), userId, toolName, action, params);

        CancellationToken cancelToken = CURRENT_CANCEL_TOKEN.get();
        if (cancelToken != null && cancelToken.isCancelled()) {
            log.info("Tool execution cancelled before start: {}:{}", toolName, action);
            ctx.setRawResult("操作已取消");
            ctx.setGuardResult(GuardResult.blocked("请求已取消"));
            postHandler.handle(ctx);
            return "操作已取消";
        }

        GuardResult guard = preGuard.check(ctx);
        ctx.setGuardResult(guard);
        if (guard.isBlocked()) {
            postHandler.handle(ctx);
            return guard.getReason();
        }

        try {
            String result = toolInvoker.get();
            ctx.setRawResult(result);
        } catch (Exception e) {
            log.error("Tool execution failed: {}:{}", toolName, action, e);
            ctx.setRawResult("操作执行异常：" + e.getMessage());
        }

        postHandler.handle(ctx);

        String result = GracefulDegradation.appendHintIfNeeded(ctx.getRawResult());
        String budgetHint = tokenBudget.getBudgetHint();
        if (!budgetHint.isEmpty()) {
            result += budgetHint;
        }
        return result;
    }
}
