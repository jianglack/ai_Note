package com.ainote.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_memory_compaction_jobs")
public class ChatMemoryCompactionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", length = 128, nullable = false)
    private String userId;

    @Column(name = "from_sequence", nullable = false)
    private Integer fromSequence;

    @Column(name = "to_sequence", nullable = false)
    private Integer toSequence;

    @Column(name = "user_message_count", nullable = false)
    private Integer userMessageCount;

    @Column(length = 30, nullable = false)
    private String status = "pending";

    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    void createTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public void setUserId(String value) { userId = value; }
    public Integer getFromSequence() { return fromSequence; }
    public void setFromSequence(Integer value) { fromSequence = value; }
    public Integer getToSequence() { return toSequence; }
    public void setToSequence(Integer value) { toSequence = value; }
    public Integer getUserMessageCount() { return userMessageCount; }
    public void setUserMessageCount(Integer value) { userMessageCount = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer value) { attempts = value; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(LocalDateTime value) { nextAttemptAt = value; }
    public LocalDateTime getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(LocalDateTime value) { leaseUntil = value; }
    public String getLastError() { return lastError; }
    public void setLastError(String value) { lastError = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { completedAt = value; }
}
