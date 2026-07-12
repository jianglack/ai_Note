package com.ainote.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 用户对话记忆实体
 * 存储 Agent 对话的消息历史，用于持久化 ChatMemory
 */
@Entity
@Table(name = "user_memories")
public class UserMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", length = 128, nullable = false)
    private String userId;

    @Column(name = "message_type", length = 30, nullable = false)
    private String messageType;  // USER, AI, SYSTEM, TOOL_EXECUTION_RESULT

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "tool_name", length = 64)
    private String toolName;

    @Column(name = "tool_result", columnDefinition = "TEXT")
    private String toolResult;

    @Column(name = "tool_calls_json", columnDefinition = "TEXT")
    private String toolCallsJson;

    @Column(name = "tool_call_id", length = 64)
    private String toolCallId;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Column(name = "trimmed_at")
    private LocalDateTime trimmedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolResult() {
        return toolResult;
    }

    public void setToolResult(String toolResult) {
        this.toolResult = toolResult;
    }

    public String getToolCallsJson() {
        return toolCallsJson;
    }

    public void setToolCallsJson(String toolCallsJson) {
        this.toolCallsJson = toolCallsJson;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Integer sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public LocalDateTime getTrimmedAt() {
        return trimmedAt;
    }

    public void setTrimmedAt(LocalDateTime trimmedAt) {
        this.trimmedAt = trimmedAt;
    }
}
