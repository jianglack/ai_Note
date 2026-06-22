package com.ainote.app.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class TaskScheduleRequest {

    @NotBlank(message = "Query is required")
    @Size(max = 8000, message = "Query must be at most 8000 characters")
    private String query;

    private Object planTemplate;

    @Pattern(regexp = "ONCE|DAILY|WEEKLY|MONTHLY", message = "Trigger type is invalid")
    private String triggerType;

    private LocalDateTime scheduledTime;

    @Min(value = 1, message = "Max run count must be at least 1")
    @Max(value = 1000, message = "Max run count must be at most 1000")
    private Integer maxRunCount;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Object getPlanTemplate() {
        return planTemplate;
    }

    public void setPlanTemplate(Object planTemplate) {
        this.planTemplate = planTemplate;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public LocalDateTime getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(LocalDateTime scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public Integer getMaxRunCount() {
        return maxRunCount;
    }

    public void setMaxRunCount(Integer maxRunCount) {
        this.maxRunCount = maxRunCount;
    }
}
