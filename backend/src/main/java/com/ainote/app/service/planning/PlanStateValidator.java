package com.ainote.app.service.planning;

import com.ainote.app.entity.TaskPlan;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 任务计划状态转换验证器 — 确保状态转换合法
 */
@Component
public class PlanStateValidator {

    /**
     * 合法的状态转换映射
     */
    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
            TaskPlan.STATUS_PLANNING, Set.of(
                    TaskPlan.STATUS_AWAITING_APPROVAL, TaskPlan.STATUS_FAILED),
            TaskPlan.STATUS_AWAITING_APPROVAL, Set.of(
                    TaskPlan.STATUS_EXECUTING, TaskPlan.STATUS_CANCELLED),
            TaskPlan.STATUS_EXECUTING, Set.of(
                    TaskPlan.STATUS_COMPLETED, TaskPlan.STATUS_FAILED,
                    TaskPlan.STATUS_PAUSED, TaskPlan.STATUS_CANCELLED,
                    TaskPlan.STATUS_CANCELLED_PARTIAL),
            TaskPlan.STATUS_PAUSED, Set.of(
                    TaskPlan.STATUS_EXECUTING, TaskPlan.STATUS_CANCELLED,
                    TaskPlan.STATUS_CANCELLED_PARTIAL),
            TaskPlan.STATUS_FAILED, Set.of(
                    TaskPlan.STATUS_EXECUTING, TaskPlan.STATUS_CANCELLED,
                    TaskPlan.STATUS_CANCELLED_PARTIAL),
            TaskPlan.STATUS_COMPLETED, Set.of(), // 终态
            TaskPlan.STATUS_CANCELLED, Set.of(), // 终态
            TaskPlan.STATUS_CANCELLED_PARTIAL, Set.of() // 终态
    );

    /**
     * 验证状态转换是否合法
     */
    public boolean canTransition(String fromStatus, String toStatus) {
        Set<String> allowed = VALID_TRANSITIONS.get(fromStatus);
        if (allowed == null) return false;
        return allowed.contains(toStatus);
    }

    /**
     * 验证并执行状态转换，不合法则抛异常
     */
    public void validateTransition(String fromStatus, String toStatus) {
        if (!canTransition(fromStatus, toStatus)) {
            throw new IllegalStateException(
                    String.format("Invalid plan status transition: %s → %s", fromStatus, toStatus));
        }
    }

    /**
     * 判断是否为终态
     */
    public boolean isTerminal(String status) {
        Set<String> allowed = VALID_TRANSITIONS.get(status);
        return allowed != null && allowed.isEmpty();
    }
}
