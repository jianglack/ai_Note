package com.ainote.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 语义记忆实体
 * 存储从对话中提炼的用户偏好、事实、习惯等长期知识
 */
@Entity
@Table(name = "semantic_memories")
public class SemanticMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    /**
     * 分类: preference(偏好), fact(事实), habit(习惯), style(交互风格)
     */
    @Column(length = 30, nullable = false)
    private String category;

    /**
     * 提炼的内容，如 "用户喜欢用 markdown 写笔记"
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * 置信度 0-1
     */
    @Column(nullable = false)
    private Double confidence = 0.8;

    /**
     * 来源: ai_extracted(AI 自动提取), user_explicit(用户明确表达)
     */
    @Column(length = 20, nullable = false)
    private String source = "ai_extracted";

    /**
     * 被多次对话印证的次数
     */
    @Column(name = "times_reinforced", nullable = false)
    private Integer timesReinforced = 1;

    @Column(name = "last_reinforced_at")
    private LocalDateTime lastReinforcedAt;

    @Column(length = 50)
    private String domain;

    @Column(nullable = false)
    private Double weight = 1.0;

    /**
     * 记忆内容的 embedding 向量（1024维），用于语义去重
     */
    @Column(columnDefinition = "vector(1024)")
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1024)
    private float[] embedding;

    /**
     * 综合衰减分数 = confidence × timesReinforced × timeDecay
     */
    @Column(name = "decay_score")
    private Double decayScore = 0.8;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (lastReinforcedAt == null) {
            lastReinforcedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Integer getTimesReinforced() { return timesReinforced; }
    public void setTimesReinforced(Integer timesReinforced) { this.timesReinforced = timesReinforced; }

    public LocalDateTime getLastReinforcedAt() { return lastReinforcedAt; }
    public void setLastReinforcedAt(LocalDateTime lastReinforcedAt) { this.lastReinforcedAt = lastReinforcedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }

    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }

    public Double getDecayScore() { return decayScore; }
    public void setDecayScore(Double decayScore) { this.decayScore = decayScore; }
}
