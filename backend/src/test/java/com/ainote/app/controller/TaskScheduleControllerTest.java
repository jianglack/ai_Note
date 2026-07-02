package com.ainote.app.controller;

import com.ainote.app.entity.TaskSchedule;
import com.ainote.app.model.TaskScheduleRequest;
import com.ainote.app.repository.TaskScheduleRepository;
import com.ainote.app.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskScheduleControllerTest {

    private TaskScheduleRepository scheduleRepository;
    private SecurityUtils securityUtils;
    private TaskScheduleController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        scheduleRepository = mock(TaskScheduleRepository.class);
        securityUtils = mock(SecurityUtils.class);
        controller = new TaskScheduleController(scheduleRepository, securityUtils);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .build();
    }

    @Test
    void listSchedulesReturnsOnlyCurrentUsersSchedules() {
        TaskSchedule schedule = schedule("schedule-1", "user-1");
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(scheduleRepository.findByUserIdOrderByCreatedAtDesc("user-1")).thenReturn(List.of(schedule));

        Object body = controller.listSchedules().getBody();

        assertThat(body).isEqualTo(List.of(schedule));
        verify(scheduleRepository).findByUserIdOrderByCreatedAtDesc("user-1");
    }

    @Test
    void createSchedulePersistsValidatedDtoForCurrentUser() {
        TaskScheduleRequest request = new TaskScheduleRequest();
        request.setQuery("整理本周项目");
        request.setPlanTemplate(Map.of("type", "plan"));
        request.setTriggerType("DAILY");
        request.setScheduledTime(LocalDateTime.parse("2026-06-28T09:00:00"));
        request.setMaxRunCount(3);

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(scheduleRepository.save(any(TaskSchedule.class))).thenAnswer(invocation -> {
            TaskSchedule saved = invocation.getArgument(0);
            saved.setId("schedule-1");
            return saved;
        });

        TaskSchedule saved = (TaskSchedule) controller.createSchedule(request).getBody();

        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getOriginalQuery()).contains("本周项目");
        assertThat(saved.getPlanTemplateJson()).contains("type");
        assertThat(saved.getTriggerType()).isEqualTo("DAILY");
        assertThat(saved.getNextRunAt()).isEqualTo(request.getScheduledTime());
        assertThat(saved.getMaxRunCount()).isEqualTo(3);
    }

    @Test
    void createScheduleValidationRejectsBadDtoBeforeSaving() throws Exception {
        mockMvc.perform(post("/api/ai/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"","triggerType":"YEARLY","maxRunCount":0}
                                """))
                .andExpect(status().isBadRequest());

        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void toggleRejectsScheduleOwnedByAnotherUser() {
        TaskSchedule schedule = schedule("schedule-1", "other-user");
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(scheduleRepository.findById("schedule-1")).thenReturn(Optional.of(schedule));

        var response = controller.toggleSchedule("schedule-1");

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void deleteDeletesOnlyCurrentUsersSchedule() {
        TaskSchedule schedule = schedule("schedule-1", "user-1");
        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
        when(scheduleRepository.findById("schedule-1")).thenReturn(Optional.of(schedule));

        var response = controller.deleteSchedule("schedule-1");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(Map.of("status", "deleted"));
        verify(scheduleRepository).delete(schedule);
    }

    private static TaskSchedule schedule(String id, String userId) {
        TaskSchedule schedule = new TaskSchedule();
        schedule.setId(id);
        schedule.setUserId(userId);
        schedule.setOriginalQuery("query");
        schedule.setPlanTemplateJson("{}");
        schedule.setTriggerType("ONCE");
        schedule.setEnabled(true);
        schedule.setCreatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        schedule.setUpdatedAt(LocalDateTime.parse("2026-06-27T10:00:00"));
        return schedule;
    }
}
