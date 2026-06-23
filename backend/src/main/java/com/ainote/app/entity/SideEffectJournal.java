package com.ainote.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "side_effect_journal")
public class SideEffectJournal {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_EXECUTED = "EXECUTED";
    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";

    @Id
    private String id;

    @Column(name = "plan_id", nullable = false)
    private String planId;

    @Column(name = "step_id", nullable = false)
    private String stepId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "original_action", length = 100, nullable = false)
    private String originalAction;

    @Column(name = "rollback_action", length = 100)
    private String rollbackAction;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(nullable = false)
    private Boolean executable = false;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "output_snapshot", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String outputSnapshot;

    @Column(length = 30, nullable = false)
    private String status = STATUS_PENDING;

    @Column(name = "journal_json", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String journalJson;

    @Column(name = "rollback_result", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String rollbackResult;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }
    public Integer getStepOrder() { return stepOrder; }
    public void setStepOrder(Integer stepOrder) { this.stepOrder = stepOrder; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getOriginalAction() { return originalAction; }
    public void setOriginalAction(String originalAction) { this.originalAction = originalAction; }
    public String getRollbackAction() { return rollbackAction; }
    public void setRollbackAction(String rollbackAction) { this.rollbackAction = rollbackAction; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public Boolean getExecutable() { return executable; }
    public void setExecutable(Boolean executable) { this.executable = executable; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOutputSnapshot() { return outputSnapshot; }
    public void setOutputSnapshot(String outputSnapshot) { this.outputSnapshot = outputSnapshot; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getJournalJson() { return journalJson; }
    public void setJournalJson(String journalJson) { this.journalJson = journalJson; }
    public String getRollbackResult() { return rollbackResult; }
    public void setRollbackResult(String rollbackResult) { this.rollbackResult = rollbackResult; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }
}
