package com.ainote.app.model;

import java.util.List;

public class ExtractedSchedule {
    private String title;
    private String startTime;
    private String endTime;
    private Boolean allDay;
    private String rrule;
    private Double confidence;
    private String source;

    public ExtractedSchedule() {}

    public ExtractedSchedule(String title, String startTime, String endTime, Boolean allDay,
                            String rrule, Double confidence, String source) {
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.allDay = allDay;
        this.rrule = rrule;
        this.confidence = confidence;
        this.source = source;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public Boolean getAllDay() { return allDay; }
    public void setAllDay(Boolean allDay) { this.allDay = allDay; }

    public String getRrule() { return rrule; }
    public void setRrule(String rrule) { this.rrule = rrule; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public static class ExtractResponse {
        private List<ExtractedSchedule> schedules;
        private String noteId;

        public ExtractResponse() {}

        public ExtractResponse(List<ExtractedSchedule> schedules, String noteId) {
            this.schedules = schedules;
            this.noteId = noteId;
        }

        public List<ExtractedSchedule> getSchedules() { return schedules; }
        public void setSchedules(List<ExtractedSchedule> schedules) { this.schedules = schedules; }

        public String getNoteId() { return noteId; }
        public void setNoteId(String noteId) { this.noteId = noteId; }
    }
}
