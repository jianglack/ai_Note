package com.ainote.ai.agent.guardrail;

public record GuardrailResult(boolean passed, String reason) {

    public static GuardrailResult ok() {
        return new GuardrailResult(true, null);
    }

    public static GuardrailResult blocked(String reason) {
        return new GuardrailResult(false, reason);
    }
}
