package com.ainote.app.service.planning;

import com.ainote.app.entity.TaskPlan;
import com.ainote.app.entity.TaskSchedule;
import com.ainote.app.repository.TaskScheduleRepository;
import com.ainote.app.service.PlanningAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

/**
 * 定时任务轮询器 — 每分钟检查到期的定时任务并触发执行
 */
@Service
public class TaskScheduleRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskScheduleRunner.class);

    private final TaskScheduleRepository scheduleRepository;
    private final PlanningAgentService planningAgentService;

    public TaskScheduleRunner(TaskScheduleRepository scheduleRepository,
                              @Lazy PlanningAgentService planningAgentService) {
        this.scheduleRepository = scheduleRepository;
        this.planningAgentService = planningAgentService;
    }

    @Scheduled(fixedDelay = 60000) // 每分钟检查一次
    public void checkAndExecute() {
        List<TaskSchedule> dueSchedules = scheduleRepository.findDueSchedules(LocalDateTime.now());
        if (dueSchedules.isEmpty()) return;

        log.info("Found {} due task schedules", dueSchedules.size());

        for (TaskSchedule schedule : dueSchedules) {
            try {
                executeDueSchedule(schedule);
            } catch (Exception e) {
                log.error("Failed to execute schedule {}: {}", schedule.getId(), e.getMessage());
            }
        }
    }

    private void executeDueSchedule(TaskSchedule schedule) {
        // 检查是否达到最大运行次数
        if (schedule.getMaxRunCount() != null && schedule.getRunCount() >= schedule.getMaxRunCount()) {
            schedule.setEnabled(false);
            scheduleRepository.save(schedule);
            log.info("Schedule {} disabled: max run count reached", schedule.getId());
            return;
        }

        log.info("Executing scheduled task: {} (trigger={})", schedule.getId(), schedule.getTriggerType());

        // 通过 smart-chat 创建新计划并自动审批执行
        Map<String, Object> result = planningAgentService.smartChat(
                schedule.getOriginalQuery(), List.of(), schedule.getUserId());

        // 如果创建了计划，自动审批执行
        String type = (String) result.get("type");
        if ("plan_created".equals(type)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> plan = (Map<String, Object>) result.get("plan");
            String planId = (String) plan.get("id");
            planningAgentService.approvePlan(planId, schedule.getUserId());
            schedule.setLastPlanId(planId);
        }

        // 更新运行状态
        schedule.setRunCount(schedule.getRunCount() + 1);
        schedule.setLastRunAt(LocalDateTime.now());
        schedule.setNextRunAt(calculateNextRun(schedule));

        // 一次性任务执行后禁用
        if (TaskSchedule.TRIGGER_ONCE.equals(schedule.getTriggerType())) {
            schedule.setEnabled(false);
        }

        scheduleRepository.save(schedule);
    }

    /**
     * 根据触发类型计算下次运行时间
     */
    private LocalDateTime calculateNextRun(TaskSchedule schedule) {
        LocalDateTime now = LocalDateTime.now();
        return switch (schedule.getTriggerType()) {
            case TaskSchedule.TRIGGER_DAILY -> now.plusDays(1);
            case TaskSchedule.TRIGGER_WEEKLY -> now.plusWeeks(1);
            case TaskSchedule.TRIGGER_MONTHLY -> now.with(TemporalAdjusters.firstDayOfNextMonth())
                    .withHour(now.getHour()).withMinute(now.getMinute());
            default -> null; // ONCE doesn't need next run
        };
    }
}
