package com.ainote.ai.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "schedule-service", fallback = ScheduleClientFallback.class)
public interface ScheduleClient {

    @GetMapping("/api/schedules")
    List<Map<String, Object>> listSchedules();

    @GetMapping("/api/schedules/today")
    List<Map<String, Object>> listTodaySchedules();

    @GetMapping("/api/schedules/upcoming")
    List<Map<String, Object>> listUpcomingSchedules();

    @PostMapping("/api/schedules")
    Map<String, Object> createSchedule(@RequestBody Map<String, Object> request);

    @PutMapping("/api/schedules/{id}")
    Map<String, Object> updateSchedule(@PathVariable("id") Long id, @RequestBody Map<String, Object> request);

    @DeleteMapping("/api/schedules/{id}")
    void deleteSchedule(@PathVariable("id") Long id);
}
