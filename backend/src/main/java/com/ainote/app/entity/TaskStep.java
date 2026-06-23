package com.ainote.app.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "task_steps")
public class TaskStep {
    @Id
    private String id;

    @Column(name = "plan_id", nullable = false)
    private String planId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(length = 100, nullable = false)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 20, nullable = false)
    private String status = "PENDING";

    @Column(name = "input_params", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String inputParams;

    @Column(name = "output_result", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String outputResult;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String compensation;

    @Column(name = "depends_on", columnDefinition = "integer[]")
    private Integer[] dependsOn;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "max_retries")
    private Integer maxRetries = 3;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";
    public static final String STATUS_BLOCKED = "BLOCKED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_ROLLED_BACK = "ROLLED_BACK";

    @PrePersist
    protected void onCreate() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public Integer getStepOrder() { return stepOrder; }
    public void setStepOrder(Integer stepOrder) { this.stepOrder = stepOrder; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getInputParams() { return inputParams; }
    public void setInputParams(String inputParams) { this.inputParams = inputParams; }
    public String getOutputResult() { return outputResult; }
    public void setOutputResult(String outputResult) { this.outputResult = outputResult; }
    public String getCompensation() { return compensation; }
    public void setCompensation(String compensation) { this.compensation = compensation; }
    public Integer[] getDependsOn() { return dependsOn; }
    public void setDependsOn(Integer[] dependsOn) { this.dependsOn = dependsOn; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
