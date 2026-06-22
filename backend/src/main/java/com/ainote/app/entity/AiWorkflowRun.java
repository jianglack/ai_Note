package com.ainote.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_workflow_runs")
public class AiWorkflowRun {
    @Id
    private String id;
    @Column(name = "workflow_id", nullable = false)
    private String workflowId;
    private String status;
    @Column(columnDefinition = "jsonb")
    private String results;
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    private String error;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResults() { return results; }
    public void setResults(String results) { this.results = results; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime t) { this.startedAt = t; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime t) { this.completedAt = t; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    @PrePersist
    protected void onCreate() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (status == null) status = "running";
        startedAt = LocalDateTime.now();
    }
}
