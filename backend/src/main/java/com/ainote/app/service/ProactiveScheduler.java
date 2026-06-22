package com.ainote.app.service;

import com.ainote.app.entity.Notification;
import com.ainote.app.entity.Schedule;
import com.ainote.app.repository.NotificationRepository;
import com.ainote.app.repository.ScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ProactiveScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProactiveScheduler.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final ScheduleRepository scheduleRepository;
    private final NotificationRepository notificationRepository;

    public ProactiveScheduler(
            ScheduleRepository scheduleRepository,
            NotificationRepository notificationRepository) {
        this.scheduleRepository = scheduleRepository;
        this.notificationRepository = notificationRepository;
    }

    @Scheduled(fixedRate = 1800000, initialDelay = 60000)
    public void checkScheduleReminders() {
        log.debug("Running schedule reminder check...");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime soonThreshold = now.plusHours(1);

        List<Schedule> schedules = scheduleRepository.findPendingDueOrSoon(soonThreshold);
        for (Schedule schedule : schedules) {
            try {
                checkSchedule(schedule, now, soonThreshold);
            } catch (Exception e) {
                log.error("Failed to check schedule {}: {}", schedule.getId(), e.getMessage());
            }
        }
    }

    private void checkSchedule(Schedule schedule, LocalDateTime now, LocalDateTime soonThreshold) {
        String userId = schedule.getUser().getId();

        if (schedule.getStartTime().isBefore(now)) {
            createNotificationIfNotExists(userId, "overdue",
                    "Schedule overdue: " + schedule.getTitle(),
                    "Scheduled time: " + schedule.getStartTime().format(FMT),
                    "scheduler", schedule.getId());
        } else if (schedule.getStartTime().isBefore(soonThreshold)) {
            createNotificationIfNotExists(userId, "reminder",
                    "Schedule due soon: " + schedule.getTitle(),
                    "Start time: " + schedule.getStartTime().format(FMT),
                    "scheduler", schedule.getId());
        }
    }

    private void createNotificationIfNotExists(String userId, String type, String title,
                                                String content, String source, String relatedId) {
        if (relatedId != null && notificationRepository
                .existsByUserIdAndRelatedIdAndTypeAndIsReadFalse(userId, relatedId, type)) {
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setSource(source);
        notification.setRelatedId(relatedId);
        notificationRepository.save(notification);

        log.info("Created {} notification for user {}: {}", type, userId, title);
    }
}
