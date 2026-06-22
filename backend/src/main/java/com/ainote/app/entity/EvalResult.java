package com.ainote.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "eval_results")
public class EvalResult {
    @Id
    private String id;

    @Column(name = "run_id", nullable = false)
    private String runId;

    @Column(name = "item_id", nullable = false)
    private String itemId;

    private Double faithfulness;

    @Column(name = "answer_relevancy")
    private Double answerRelevancy;

    @Column(name = "context_precision")
    private Double contextPrecision;

    @Column(name = "context_recall")
    private Double contextRecall;

    @Column(name = "task_completion")
    private Double taskCompletion;

    @Column(name = "tool_accuracy")
    private Double toolAccuracy;

    private Double consistency;

    @Column(name = "error_recovery_rate")
    private Double errorRecoveryRate;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "actual_answer", columnDefinition = "TEXT")
    private String actualAnswer;

    @Column(name = "actual_contexts", columnDefinition = "jsonb")
    private String actualContexts;

    @Column(name = "actual_tool_calls", columnDefinition = "jsonb")
    private String actualToolCalls;

    @Column(name = "evaluation_details", columnDefinition = "jsonb")
    private String evaluationDetails;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public Double getFaithfulness() { return faithfulness; }
    public void setFaithfulness(Double faithfulness) { this.faithfulness = faithfulness; }
    public Double getAnswerRelevancy() { return answerRelevancy; }
    public void setAnswerRelevancy(Double answerRelevancy) { this.answerRelevancy = answerRelevancy; }
    public Double getContextPrecision() { return contextPrecision; }
    public void setContextPrecision(Double contextPrecision) { this.contextPrecision = contextPrecision; }
    public Double getContextRecall() { return contextRecall; }
    public void setContextRecall(Double contextRecall) { this.contextRecall = contextRecall; }
    public Double getTaskCompletion() { return taskCompletion; }
    public void setTaskCompletion(Double taskCompletion) { this.taskCompletion = taskCompletion; }
    public Double getToolAccuracy() { return toolAccuracy; }
    public void setToolAccuracy(Double toolAccuracy) { this.toolAccuracy = toolAccuracy; }
    public Double getConsistency() { return consistency; }
    public void setConsistency(Double consistency) { this.consistency = consistency; }
    public Double getErrorRecoveryRate() { return errorRecoveryRate; }
    public void setErrorRecoveryRate(Double errorRecoveryRate) { this.errorRecoveryRate = errorRecoveryRate; }
    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
    public String getActualAnswer() { return actualAnswer; }
    public void setActualAnswer(String actualAnswer) { this.actualAnswer = actualAnswer; }
    public String getActualContexts() { return actualContexts; }
    public void setActualContexts(String actualContexts) { this.actualContexts = actualContexts; }
    public String getActualToolCalls() { return actualToolCalls; }
    public void setActualToolCalls(String actualToolCalls) { this.actualToolCalls = actualToolCalls; }
    public String getEvaluationDetails() { return evaluationDetails; }
    public void setEvaluationDetails(String evaluationDetails) { this.evaluationDetails = evaluationDetails; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @PrePersist
    protected void onCreate() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        createdAt = LocalDateTime.now();
    }
}
