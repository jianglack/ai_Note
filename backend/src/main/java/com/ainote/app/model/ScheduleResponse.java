package com.ainote.app.model;

import com.ainote.app.entity.Schedule;
import java.time.LocalDateTime;
import java.util.List;

public class ScheduleResponse {
    private String id;
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean allDay;
    private String rrule;
    private Integer reminderMinutes;
    private String status;
    private List<NoteRef> notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static class NoteRef {
        private String id;
        private String title;

        public NoteRef(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
    }

    public static ScheduleResponse from(Schedule s, String computedStatus) {
        ScheduleResponse r = new ScheduleResponse();
        r.id = s.getId();
        r.title = s.getTitle();
        r.description = s.getDescription();
        r.startTime = s.getStartTime();
        r.endTime = s.getEndTime();
        r.allDay = s.getAllDay();
        r.rrule = s.getRrule();
        r.reminderMinutes = s.getReminderMinutes();
        r.status = computedStatus;
        r.notes = s.getNotes() == null
                ? List.of()
                : s.getNotes().stream()
                    .map(note -> new NoteRef(note.getId(), note.getTitle()))
                    .toList();
        r.createdAt = s.getCreatedAt();
        r.updatedAt = s.getUpdatedAt();
        return r;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public Boolean getAllDay() { return allDay; }
    public String getRrule() { return rrule; }
    public Integer getReminderMinutes() { return reminderMinutes; }
    public String getStatus() { return status; }
    public List<NoteRef> getNotes() { return notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
