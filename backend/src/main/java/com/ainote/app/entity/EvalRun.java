package com.ainote.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "eval_runs")
public class EvalRun {
    @Id
    private String id;

    @Column(name = "dataset_id", nullable = false)
    private String datasetId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "run_type", nullable = false)
    private String runType;

    private String status;

    @Column(columnDefinition = "jsonb")
    private String config;

    @Column(columnDefinition = "jsonb")
    private String summary;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message")
    private String errorMessage;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDatasetId() { return datasetId; }
    public void setDatasetId(String datasetId) { this.datasetId = datasetId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getRunType() { return runType; }
    public void setRunType(String runType) { this.runType = runType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    @PrePersist
    protected void onCreate() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (status == null) status = "running";
        startedAt = LocalDateTime.now();
    }
}
