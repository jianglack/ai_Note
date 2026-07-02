package com.ainote.app.service.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ainote.app.entity.TaskSchedule;
import com.ainote.app.repository.TaskScheduleRepository;
import com.ainote.app.service.PlanningAgentService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TaskScheduleRunnerTest {

    private TaskScheduleRepository scheduleRepository;
    private PlanningAgentService planningAgentService;
    private TaskScheduleRunner runner;

    @BeforeEach
    void setUp() {
        scheduleRepository = mock(TaskScheduleRepository.class);
        planningAgentService = mock(PlanningAgentService.class);
        runner = new TaskScheduleRunner(scheduleRepository, planningAgentService);
    }

    @Test
    void checkAndExecuteDoesNothingWhenNoScheduleIsDue() {
        when(scheduleRepository.findDueSchedules(any(LocalDateTime.class))).thenReturn(List.of());

        runner.checkAndExecute();

        verify(planningAgentService, never()).smartChat(any(), any(), any());
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void checkAndExecuteCreatesAndApprovesPlanForDueOnceSchedule() {
        TaskSchedule schedule = schedule("schedule-1", TaskSchedule.TRIGGER_ONCE);
        when(scheduleRepository.findDueSchedules(any(LocalDateTime.class))).thenReturn(List.of(schedule));
        when(planningAgentService.smartChat("整理项目", List.of(), "user-1"))
                .thenReturn(Map.of("type", "plan_created", "plan", Map.of("id", "plan-1")));

        runner.checkAndExecute();

        verify(planningAgentService).approvePlan("plan-1", "user-1");
        ArgumentCaptor<TaskSchedule> captor = ArgumentCaptor.forClass(TaskSchedule.class);
        verify(scheduleRepository).save(captor.capture());
        TaskSchedule saved = captor.getValue();
        assertThat(saved.getRunCount()).isEqualTo(1);
        assertThat(saved.getEnabled()).isFalse();
        assertThat(saved.getLastPlanId()).isEqualTo("plan-1");
        assertThat(saved.getLastRunAt()).isNotNull();
        assertThat(saved.getNextRunAt()).isNull();
    }

    @Test
    void checkAndExecuteDisablesScheduleAtMaxRunCountWithoutExecuting() {
        TaskSchedule schedule = schedule("schedule-1", TaskSchedule.TRIGGER_DAILY);
        schedule.setRunCount(3);
        schedule.setMaxRunCount(3);
        when(scheduleRepository.findDueSchedules(any(LocalDateTime.class))).thenReturn(List.of(schedule));

        runner.checkAndExecute();

        verify(planningAgentService, never()).smartChat(any(), any(), any());
        verify(scheduleRepository).save(schedule);
        assertThat(schedule.getEnabled()).isFalse();
    }

    @Test
    void checkAndExecuteKeepsRecurringScheduleEnabledAndSetsNextRun() {
        TaskSchedule schedule = schedule("schedule-1", TaskSchedule.TRIGGER_DAILY);
        when(scheduleRepository.findDueSchedules(any(LocalDateTime.class))).thenReturn(List.of(schedule));
        when(planningAgentService.smartChat("整理项目", List.of(), "user-1"))
                .thenReturn(Map.of("type", "direct", "content", "done"));

        runner.checkAndExecute();

        verify(planningAgentService, never()).approvePlan(any(), any());
        assertThat(schedule.getRunCount()).isEqualTo(1);
        assertThat(schedule.getEnabled()).isTrue();
        assertThat(schedule.getNextRunAt()).isAfter(LocalDateTime.now());
        verify(scheduleRepository).save(schedule);
    }

    private static TaskSchedule schedule(String id, String triggerType) {
        TaskSchedule schedule = new TaskSchedule();
        schedule.setId(id);
        schedule.setUserId("user-1");
        schedule.setOriginalQuery("整理项目");
        schedule.setPlanTemplateJson("{}");
        schedule.setTriggerType(triggerType);
        schedule.setEnabled(true);
        schedule.setRunCount(0);
        schedule.setNextRunAt(LocalDateTime.now().minusMinutes(1));
        return schedule;
    }
}
