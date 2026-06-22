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

    @Column(name = "user_id", length = 36, nullable = false)
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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
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
}
