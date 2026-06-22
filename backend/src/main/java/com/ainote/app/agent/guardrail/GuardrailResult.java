package com.ainote.app.agent.guardrail;

/**
 * 护栏检查结果。
 * passed=true 表示通过，passed=false 表示被拦截并携带拦截原因。
 */
public record GuardrailResult(boolean passed, String reason) {

    public static GuardrailResult ok() {
        return new GuardrailResult(true, null);
    }

    public static GuardrailResult blocked(String reason) {
        return new GuardrailResult(false, reason);
    }
}
