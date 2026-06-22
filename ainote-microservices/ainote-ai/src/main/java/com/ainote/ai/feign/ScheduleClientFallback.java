package com.ainote.ai.feign;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ScheduleClientFallback implements ScheduleClient {

    @Override
    public List<Map<String, Object>> listSchedules() {
        return List.of();
    }

    @Override
    public List<Map<String, Object>> listTodaySchedules() {
        return List.of();
    }

    @Override
    public List<Map<String, Object>> listUpcomingSchedules() {
        return List.of();
    }

    @Override
    public Map<String, Object> createSchedule(Map<String, Object> request) {
        return Map.of("error", "schedule-service unavailable");
    }

    @Override
    public Map<String, Object> updateSchedule(Long id, Map<String, Object> request) {
        return Map.of("error", "schedule-service unavailable");
    }

    @Override
    public void deleteSchedule(Long id) { }
}
