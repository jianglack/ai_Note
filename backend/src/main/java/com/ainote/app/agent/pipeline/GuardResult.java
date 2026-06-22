package com.ainote.app.agent.pipeline;

/**
 * 前置守卫检查结果。
 * 通过时 blocked=false，被拦截时携带拦截原因。
 */
public record GuardResult(boolean blocked, String reason) {

    public static GuardResult passed() {
        return new GuardResult(false, null);
    }

    public static GuardResult blocked(String reason) {
        return new GuardResult(true, reason);
    }

    public boolean isBlocked() {
        return blocked;
    }

    public String getReason() {
        return reason;
    }
}
