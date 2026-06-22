package com.ainote.schedule.controller;

import com.ainote.schedule.model.ScheduleModel;
import com.ainote.schedule.model.ScheduleRequest;
import com.ainote.schedule.service.ScheduleService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public List<ScheduleModel> getSchedules(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return scheduleService.getSchedules(startDate, endDate);
    }

    @GetMapping("/{id}")
    public ScheduleModel getSchedule(@PathVariable String id) {
        return scheduleService.getSchedule(id);
    }

    @PostMapping
    public ScheduleModel createSchedule(@RequestBody ScheduleRequest request) {
        return scheduleService.createSchedule(request);
    }

    @PutMapping("/{id}")
    public ScheduleModel updateSchedule(@PathVariable String id, @RequestBody ScheduleRequest request) {
        return scheduleService.updateSchedule(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable String id) {
        scheduleService.deleteSchedule(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/status")
    public ScheduleModel updateStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return scheduleService.updateStatus(id, status);
    }

    @GetMapping("/today")
    public List<ScheduleModel> getTodaySchedules() {
        return scheduleService.getTodaySchedules();
    }

    @GetMapping("/upcoming")
    public List<ScheduleModel> getUpcomingSchedules() {
        return scheduleService.getUpcomingSchedules();
    }
}
