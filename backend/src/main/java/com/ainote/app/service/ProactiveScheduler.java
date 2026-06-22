package com.ainote.app.service;

import com.ainote.app.entity.Notification;
import com.ainote.app.entity.Schedule;
import com.ainote.app.entity.User;
import com.ainote.app.repository.NotificationRepository;
import com.ainote.app.repository.ScheduleRepository;
import com.ainote.app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 主动智能调度器
 * 后台定时检查：过期日程提醒、即将到期提醒、每日摘要
 */
@Service
public class ProactiveScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProactiveScheduler.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final ScheduleRepository scheduleRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public ProactiveScheduler(
            ScheduleRepository scheduleRepository,
            NotificationRepository notificationRepository,
            UserRepository userRepository) {
        this.scheduleRepository = scheduleRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    /**
     * 每 30 分钟检查过期和即将到期的日程
     */
    @Scheduled(fixedRate = 1800000, initialDelay = 60000)
    public void checkScheduleReminders() {
        log.debug("Running schedule reminder check...");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime soonThreshold = now.plusHours(1);

        List<User> users = userRepository.findAll();
        for (User user : users) {
            try {
                checkUserSchedules(user.getId(), now, soonThreshold);
            } catch (Exception e) {
                log.error("Failed to check schedules for user {}: {}", user.getId(), e.getMessage());
            }
        }
    }

    private void checkUserSchedules(String userId, LocalDateTime now, LocalDateTime soonThreshold) {
        List<Schedule> schedules = scheduleRepository.findByUserIdOrderByStartTimeDesc(userId);

        for (Schedule schedule : schedules) {
            if (!"pending".equals(schedule.getStatus())) continue;

            if (schedule.getStartTime().isBefore(now)) {
                // 已过期
                createNotificationIfNotExists(userId, "overdue",
                        "日程已过期: " + schedule.getTitle(),
                        "原定时间: " + schedule.getStartTime().format(FMT) + "，请处理或标记完成",
                        "scheduler", schedule.getId());
            } else if (schedule.getStartTime().isBefore(soonThreshold)) {
                // 即将到期（1小时内）
                createNotificationIfNotExists(userId, "reminder",
                        "即将到期: " + schedule.getTitle(),
                        "开始时间: " + schedule.getStartTime().format(FMT),
                        "scheduler", schedule.getId());
            }
        }
    }

    private void createNotificationIfNotExists(String userId, String type, String title,
                                                String content, String source, String relatedId) {
        // Check if we already notified about this (avoid spam)
        List<Notification> existing = notificationRepository
                .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);

        boolean alreadyNotified = existing.stream()
                .anyMatch(n -> relatedId != null && relatedId.equals(n.getRelatedId())
                        && type.equals(n.getType()));

        if (alreadyNotified) return;

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
