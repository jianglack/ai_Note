package com.ainote.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "task_schedules")
public class TaskSchedule {
    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "plan_template_json", nullable = false, columnDefinition = "jsonb")
    private String planTemplateJson;

    @Column(name = "original_query", nullable = false, columnDefinition = "TEXT")
    private String originalQuery;

    @Column(name = "trigger_type", length = 20, nullable = false)
    private String triggerType = TRIGGER_ONCE;

    @Column(name = "cron_expression", length = 100)
    private String cronExpression;

    @Column(name = "scheduled_time")
    private LocalDateTime scheduledTime;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "max_run_count")
    private Integer maxRunCount;

    @Column(name = "run_count")
    private Integer runCount = 0;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    @Column(name = "last_plan_id")
    private String lastPlanId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static final String TRIGGER_ONCE = "ONCE";
    public static final String TRIGGER_DAILY = "DAILY";
    public static final String TRIGGER_WEEKLY = "WEEKLY";
    public static final String TRIGGER_MONTHLY = "MONTHLY";

    @PrePersist
    protected void onCreate() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getPlanTemplateJson() { return planTemplateJson; }
    public void setPlanTemplateJson(String planTemplateJson) { this.planTemplateJson = planTemplateJson; }
    public String getOriginalQuery() { return originalQuery; }
    public void setOriginalQuery(String originalQuery) { this.originalQuery = originalQuery; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public LocalDateTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalDateTime scheduledTime) { this.scheduledTime = scheduledTime; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getMaxRunCount() { return maxRunCount; }
    public void setMaxRunCount(Integer maxRunCount) { this.maxRunCount = maxRunCount; }
    public Integer getRunCount() { return runCount; }
    public void setRunCount(Integer runCount) { this.runCount = runCount; }
    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    public LocalDateTime getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(LocalDateTime nextRunAt) { this.nextRunAt = nextRunAt; }
    public String getLastPlanId() { return lastPlanId; }
    public void setLastPlanId(String lastPlanId) { this.lastPlanId = lastPlanId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
