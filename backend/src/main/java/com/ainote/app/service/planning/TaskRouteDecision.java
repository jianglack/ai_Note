package com.ainote.app.service.planning;

public record TaskRouteDecision(
        TaskRoute route,
        double confidence,
        String reason,
        boolean requiresUserPlanApproval,
        int estimatedToolSteps,
        String riskLevel
) {
    public static TaskRouteDecision directFallback(String reason) {
        return new TaskRouteDecision(
                TaskRoute.DIRECT_AGENT,
                0.0,
                reason,
                false,
                0,
                "LOW"
        );
    }
}
