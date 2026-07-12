package com.ainote.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_memory_heads")
public class ChatMemoryHead {

    @Id
    @Column(name = "user_id", length = 128)
    private String userId;

    @Column(name = "next_sequence_number", nullable = false)
    private Integer nextSequenceNumber = 0;

    @Column(name = "last_compaction_enqueued_sequence", nullable = false)
    private Integer lastCompactionEnqueuedSequence = -1;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Integer getNextSequenceNumber() { return nextSequenceNumber; }
    public void setNextSequenceNumber(Integer value) { nextSequenceNumber = value; }
    public Integer getLastCompactionEnqueuedSequence() { return lastCompactionEnqueuedSequence; }
    public void setLastCompactionEnqueuedSequence(Integer value) { lastCompactionEnqueuedSequence = value; }
    public Long getVersion() { return version; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
