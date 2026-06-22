package com.ainote.app.controller;

import com.ainote.app.model.ScheduleRequest;
import com.ainote.app.model.ScheduleResponse;
import com.ainote.app.model.ScheduleStatusRequest;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final SecurityUtils securityUtils;

    public ScheduleController(ScheduleService scheduleService, SecurityUtils securityUtils) {
        this.scheduleService = scheduleService;
        this.securityUtils = securityUtils;
    }

    @GetMapping
    public List<ScheduleResponse> getSchedules(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        String userId = securityUtils.getCurrentUserId();
        return scheduleService.getSchedules(userId, startDate, endDate);
    }

    @GetMapping("/{id}")
    public ScheduleResponse getSchedule(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        return scheduleService.getSchedule(id, userId);
    }

    @PostMapping
    public ScheduleResponse createSchedule(@Valid @RequestBody ScheduleRequest request) {
        String userId = securityUtils.getCurrentUserId();
        return scheduleService.createSchedule(request, userId);
    }

    @PutMapping("/{id}")
    public ScheduleResponse updateSchedule(@PathVariable String id, @Valid @RequestBody ScheduleRequest request) {
        String userId = securityUtils.getCurrentUserId();
        return scheduleService.updateSchedule(id, request, userId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable String id) {
        String userId = securityUtils.getCurrentUserId();
        scheduleService.deleteSchedule(id, userId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/status")
    public ScheduleResponse updateStatus(@PathVariable String id, @Valid @RequestBody ScheduleStatusRequest request) {
        String userId = securityUtils.getCurrentUserId();
        return scheduleService.updateStatus(id, request.getStatus(), userId);
    }
}
