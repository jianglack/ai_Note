package com.ainote.app.agent.pipeline;

import com.ainote.app.agent.CancellationToken;
import com.ainote.app.agent.budget.TokenBudget;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 工具执行管道 — 所有 Agent 工具调用的统一入口。
 * <p>
 * 执行流程：
 * <pre>
 * 取消检查（CancellationToken）
 *     ↓
 * 前置守卫（参数校验 + 循环检测 + 幂等）
 *     ↓ 通过
 * 实际执行（toolInvoker.get()）
 *     ↓
 * 后置处理（结构化解析 + 副作用记录 + 审计日志）
 *     ↓
 * 优雅降级提示 + Token 预算提示
 *     ↓
 * 返回结果
 * </pre>
 * </p>
 */
@Component
public class ToolExecutionPipeline {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionPipeline.class);

    /**
     * 当前请求的取消令牌（ThreadLocal，由 AgentService 在调用前设置）
     */
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

    /**
     * 设置当前请求的取消令牌。由 AgentService 在 Agent 调用前设置。
     */
    public static void setCancelToken(CancellationToken token) {
        CURRENT_CANCEL_TOKEN.set(token);
    }

    /**
     * 清除取消令牌。由 AgentService 在 Agent 调用结束后清除。
     */
    public static void clearCancelToken() {
        CURRENT_CANCEL_TOKEN.remove();
    }

    /**
     * 统一执行入口。
     *
     * @param userId      当前用户 ID
     * @param toolName    工具名称（如 "noteAction"）
     * @param action      操作类型（如 "create"）
     * @param params      解析后的参数 JsonNode
     * @param toolInvoker 实际执行逻辑（延迟执行，仅在守卫通过后调用）
     * @return 工具返回字符串（可能被拦截信息或降级提示替代/追加）
     */
    public String execute(String userId, String toolName, String action,
                          JsonNode params, Supplier<String> toolInvoker) {
        ToolExecutionContext ctx = new ToolExecutionContext(
                UUID.randomUUID().toString(), userId, toolName, action, params);

        // ---- 取消检查 ----
        CancellationToken cancelToken = CURRENT_CANCEL_TOKEN.get();
        if (cancelToken != null && cancelToken.isCancelled()) {
            log.info("Tool execution cancelled before start: {}:{}", toolName, action);
            ctx.setRawResult("操作已取消");
            ctx.setGuardResult(GuardResult.blocked("请求已取消"));
            postHandler.handle(ctx);
            return "操作已取消";
        }

        // ---- 前置守卫 ----
        GuardResult guard = preGuard.check(ctx);
        ctx.setGuardResult(guard);
        if (guard.isBlocked()) {
            postHandler.handle(ctx);
            return guard.getReason();
        }

        // ---- 实际执行 ----
        try {
            String result = toolInvoker.get();
            ctx.setRawResult(result);
        } catch (Exception e) {
            log.error("Tool execution failed: {}:{}", toolName, action, e);
            ctx.setRawResult("操作执行异常：" + e.getMessage());
        }

        // ---- 后置处理 ----
        postHandler.handle(ctx);

        // ---- 优雅降级提示 + Token 预算提示 ----
        String result = GracefulDegradation.appendHintIfNeeded(ctx.getRawResult());
        String budgetHint = tokenBudget.getBudgetHint();
        if (!budgetHint.isEmpty()) {
            result += budgetHint;
        }
        return result;
    }
}
