package com.ainote.schedule.model;

import com.ainote.schedule.entity.Schedule;

import java.time.LocalDateTime;

public class ScheduleModel {
    private String id;
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean allDay;
    private String rrule;
    private Integer reminderMinutes;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ScheduleModel from(Schedule s, String computedStatus) {
        ScheduleModel m = new ScheduleModel();
        m.id = s.getId();
        m.title = s.getTitle();
        m.description = s.getDescription();
        m.startTime = s.getStartTime();
        m.endTime = s.getEndTime();
        m.allDay = s.getAllDay();
        m.rrule = s.getRrule();
        m.reminderMinutes = s.getReminderMinutes();
        m.status = computedStatus;
        m.createdAt = s.getCreatedAt();
        m.updatedAt = s.getUpdatedAt();
        return m;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Boolean getAllDay() { return allDay; }
    public void setAllDay(Boolean allDay) { this.allDay = allDay; }

    public String getRrule() { return rrule; }
    public void setRrule(String rrule) { this.rrule = rrule; }

    public Integer getReminderMinutes() { return reminderMinutes; }
    public void setReminderMinutes(Integer reminderMinutes) { this.reminderMinutes = reminderMinutes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
