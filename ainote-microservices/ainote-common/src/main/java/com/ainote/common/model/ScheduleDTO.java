package com.ainote.common.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScheduleDTO {
    private Long id;
    private Long userId;
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private String rrule;
    private Integer reminderMinutes;
    private LocalDateTime createdAt;
}
