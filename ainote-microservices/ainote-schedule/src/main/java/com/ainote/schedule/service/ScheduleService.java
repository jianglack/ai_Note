package com.ainote.schedule.service;

import com.ainote.common.security.UserContext;
import com.ainote.schedule.entity.Schedule;
import com.ainote.schedule.model.ScheduleModel;
import com.ainote.schedule.model.ScheduleRequest;
import com.ainote.schedule.repository.ScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    private final ScheduleRepository scheduleRepository;

    public ScheduleService(ScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    private String getCurrentUserId() {
        return String.valueOf(UserContext.getCurrentUserId());
    }

    @Transactional(readOnly = true)
    public List<ScheduleModel> getSchedules(LocalDateTime startDate, LocalDateTime endDate) {
        String userId = getCurrentUserId();
        List<Schedule> schedules;
        if (startDate != null && endDate != null) {
            schedules = scheduleRepository.findByUserIdAndDateRange(userId, startDate, endDate);
        } else {
            schedules = scheduleRepository.findByUserIdOrderByStartTimeDesc(userId);
        }
        return schedules.stream()
                .map(s -> ScheduleModel.from(s, computeStatus(s)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ScheduleModel getSchedule(String id) {
        String userId = getCurrentUserId();
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Schedule not found"));
        if (!schedule.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }
        return ScheduleModel.from(schedule, computeStatus(schedule));
    }

    public ScheduleModel createSchedule(ScheduleRequest req) {
        String userId = getCurrentUserId();
        Schedule schedule = new Schedule();
        schedule.setId(UUID.randomUUID().toString());
        schedule.setTitle(req.getTitle());
        schedule.setDescription(req.getDescription());
        schedule.setUserId(userId);
        schedule.setStartTime(req.getStartTime());
        schedule.setEndTime(req.getEndTime());
        schedule.setAllDay(req.getAllDay() != null ? req.getAllDay() : false);
        schedule.setRrule(req.getRrule());
        schedule.setReminderMinutes(req.getReminderMinutes());
        schedule.setStatus("PENDING");
        schedule.setCreatedAt(LocalDateTime.now());
        schedule.setUpdatedAt(LocalDateTime.now());

        scheduleRepository.save(schedule);
        log.info("Schedule created: {}", schedule.getId());
        return ScheduleModel.from(schedule, computeStatus(schedule));
    }

    public ScheduleModel updateSchedule(String id, ScheduleRequest req) {
        String userId = getCurrentUserId();
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Schedule not found"));
        if (!schedule.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        boolean timeChanged = !schedule.getStartTime().equals(req.getStartTime()) ||
                (req.getEndTime() != null && !req.getEndTime().equals(schedule.getEndTime()));

        schedule.setTitle(req.getTitle());
        schedule.setDescription(req.getDescription());
        schedule.setStartTime(req.getStartTime());
        schedule.setEndTime(req.getEndTime());
        schedule.setAllDay(req.getAllDay() != null ? req.getAllDay() : false);
        schedule.setRrule(req.getRrule());
        schedule.setReminderMinutes(req.getReminderMinutes());
        schedule.setUpdatedAt(LocalDateTime.now());

        if (timeChanged && "completed".equalsIgnoreCase(schedule.getStatus())) {
            schedule.setStatus("PENDING");
        }

        scheduleRepository.save(schedule);
        return ScheduleModel.from(schedule, computeStatus(schedule));
    }

    public void deleteSchedule(String id) {
        String userId = getCurrentUserId();
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Schedule not found"));
        if (!schedule.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }
        scheduleRepository.delete(schedule);
        log.info("Schedule deleted: {}", id);
    }

    public ScheduleModel updateStatus(String id, String status) {
        String userId = getCurrentUserId();
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Schedule not found"));
        if (!schedule.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }
        schedule.setStatus(status);
        schedule.setUpdatedAt(LocalDateTime.now());
        scheduleRepository.save(schedule);
        return ScheduleModel.from(schedule, computeStatus(schedule));
    }

    @Transactional(readOnly = true)
    public List<ScheduleModel> getTodaySchedules() {
        String userId = getCurrentUserId();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        return scheduleRepository.findTodaySchedules(userId, startOfDay, endOfDay).stream()
                .map(s -> ScheduleModel.from(s, computeStatus(s)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ScheduleModel> getUpcomingSchedules() {
        String userId = getCurrentUserId();
        return scheduleRepository.findUpcoming(userId, LocalDateTime.now()).stream()
                .map(s -> ScheduleModel.from(s, computeStatus(s)))
                .collect(Collectors.toList());
    }

    private String computeStatus(Schedule schedule) {
        if ("completed".equalsIgnoreCase(schedule.getStatus())) {
            return "completed";
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = schedule.getEndTime() != null ? schedule.getEndTime() : schedule.getStartTime();
        if (now.isAfter(endTime)) {
            return "expired";
        }
        if (now.isAfter(schedule.getStartTime()) || now.isEqual(schedule.getStartTime())) {
            return "in_progress";
        }
        return "pending";
    }
}
