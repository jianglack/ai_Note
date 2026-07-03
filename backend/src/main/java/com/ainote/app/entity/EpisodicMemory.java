package com.ainote.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 情节记忆实体
 * 存储每次会话结束时的整体摘要
 */
@Entity
@Table(name = "episodic_memories")
public class EpisodicMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", length = 128, nullable = false)
    private String userId;

    /**
     * 本次会话摘要
     */
    @Column(name = "session_summary", columnDefinition = "TEXT", nullable = false)
    private String sessionSummary;

    /**
     * 主要话题（JSON 数组）
     */
    @Column(name = "key_topics", columnDefinition = "TEXT")
    private String keyTopics;

    /**
     * 执行的操作（JSON 数组）
     */
    @Column(name = "actions_taken", columnDefinition = "TEXT")
    private String actionsTaken;

    /**
     * 本次会话消息数
     */
    @Column(name = "message_count")
    private Integer messageCount = 0;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "thread_id", length = 128)
    private String threadId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Transient
    private float[] summaryEmbedding;

    @Column(length = 30)
    private String status = "active";

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "source_message_range", columnDefinition = "TEXT")
    private String sourceMessageRange;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "active";
        }
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getSessionSummary() { return sessionSummary; }
    public void setSessionSummary(String sessionSummary) { this.sessionSummary = sessionSummary; }

    public String getKeyTopics() { return keyTopics; }
    public void setKeyTopics(String keyTopics) { this.keyTopics = keyTopics; }

    public String getActionsTaken() { return actionsTaken; }
    public void setActionsTaken(String actionsTaken) { this.actionsTaken = actionsTaken; }

    public Integer getMessageCount() { return messageCount; }
    public void setMessageCount(Integer messageCount) { this.messageCount = messageCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }

    public float[] getSummaryEmbedding() { return summaryEmbedding; }
    public void setSummaryEmbedding(float[] summaryEmbedding) { this.summaryEmbedding = summaryEmbedding; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public String getSourceMessageRange() { return sourceMessageRange; }
    public void setSourceMessageRange(String sourceMessageRange) { this.sourceMessageRange = sourceMessageRange; }
}
