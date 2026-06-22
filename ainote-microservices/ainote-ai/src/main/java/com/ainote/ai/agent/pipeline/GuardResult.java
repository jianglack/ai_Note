package com.ainote.ai.agent.pipeline;

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
