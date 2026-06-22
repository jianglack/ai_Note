package com.ainote.app.controller;

import com.ainote.app.entity.TaskSchedule;
import com.ainote.app.model.TaskScheduleRequest;
import com.ainote.app.repository.TaskScheduleRepository;
import com.ainote.app.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/schedules")
public class TaskScheduleController {

    private final TaskScheduleRepository scheduleRepository;
    private final SecurityUtils securityUtils;

    public TaskScheduleController(TaskScheduleRepository scheduleRepository,
                                  SecurityUtils securityUtils) {
        this.scheduleRepository = scheduleRepository;
        this.securityUtils = securityUtils;
    }

    @GetMapping
    public ResponseEntity<?> listSchedules() {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(scheduleRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    @PostMapping
    public ResponseEntity<?> createSchedule(@Valid @RequestBody TaskScheduleRequest body) {
        String userId = securityUtils.getCurrentUserId();

        TaskSchedule schedule = new TaskSchedule();
        schedule.setUserId(userId);
        schedule.setOriginalQuery(body.getQuery());
        schedule.setPlanTemplateJson(body.getPlanTemplate() != null ? body.getPlanTemplate().toString() : "{}");
        schedule.setTriggerType(body.getTriggerType() != null ? body.getTriggerType() : "ONCE");

        if (body.getScheduledTime() != null) {
            schedule.setScheduledTime(body.getScheduledTime());
            schedule.setNextRunAt(schedule.getScheduledTime());
        } else {
            schedule.setNextRunAt(LocalDateTime.now());
        }

        if (body.getMaxRunCount() != null) {
            schedule.setMaxRunCount(body.getMaxRunCount());
        }

        schedule = scheduleRepository.save(schedule);
        return ResponseEntity.ok(schedule);
    }

    @PutMapping("/{id}/toggle")
    public ResponseEntity<?> toggleSchedule(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        TaskSchedule schedule = scheduleRepository.findById(id).orElseThrow();
        if (!schedule.getUserId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        schedule.setEnabled(!schedule.getEnabled());
        scheduleRepository.save(schedule);
        return ResponseEntity.ok(Map.of("enabled", schedule.getEnabled()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSchedule(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        TaskSchedule schedule = scheduleRepository.findById(id).orElseThrow();
        if (!schedule.getUserId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        scheduleRepository.delete(schedule);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }
}
