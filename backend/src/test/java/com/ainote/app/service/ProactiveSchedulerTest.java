package com.ainote.app.service;

import com.ainote.app.entity.Notification;
import com.ainote.app.entity.Schedule;
import com.ainote.app.entity.User;
import com.ainote.app.repository.NotificationRepository;
import com.ainote.app.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProactiveSchedulerTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private NotificationRepository notificationRepository;

    private ProactiveScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ProactiveScheduler(scheduleRepository, notificationRepository);
    }

    @Test
    void checkScheduleReminders_usesSetBasedScheduleQueryAndExistsChecks() {
        User user = new User();
        user.setId("user-1");

        Schedule overdue = schedule("overdue-1", user, LocalDateTime.now().minusMinutes(10));
        Schedule soon = schedule("soon-1", user, LocalDateTime.now().plusMinutes(30));

        when(scheduleRepository.findPendingDueOrSoon(any(LocalDateTime.class)))
                .thenReturn(List.of(overdue, soon));
        when(notificationRepository.existsByUserIdAndRelatedIdAndTypeAndIsReadFalse("user-1", "overdue-1", "overdue"))
                .thenReturn(false);
        when(notificationRepository.existsByUserIdAndRelatedIdAndTypeAndIsReadFalse("user-1", "soon-1", "reminder"))
                .thenReturn(false);

        scheduler.checkScheduleReminders();

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.times(2)).save(saved.capture());

        assertThat(saved.getAllValues())
                .extracting(Notification::getType)
                .containsExactlyInAnyOrder("overdue", "reminder");
        verify(scheduleRepository, never()).findByUserIdOrderByStartTimeDesc(anyString());
        verify(notificationRepository, never()).findByUserIdAndIsReadFalseOrderByCreatedAtDesc(anyString());
    }

    private static Schedule schedule(String id, User user, LocalDateTime startTime) {
        Schedule schedule = new Schedule();
        schedule.setId(id);
        schedule.setTitle(id);
        schedule.setUser(user);
        schedule.setStatus("pending");
        schedule.setStartTime(startTime);
        return schedule;
    }
}
