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
@Table(name = "memory_review_cases")
public class MemoryReviewCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", length = 128, nullable = false)
    private String userId;

    @Column(name = "memory_id")
    private Long memoryId;

    @Column(name = "feedback_type", length = 40, nullable = false)
    private String feedbackType;

    @Column(name = "user_comment", columnDefinition = "TEXT")
    private String userComment;

    @Column(name = "expected_content", columnDefinition = "TEXT")
    private String expectedContent;

    @Column(name = "expected_memory_type", length = 40)
    private String expectedMemoryType;

    @Column(name = "expected_capture_allowed")
    private Boolean expectedCaptureAllowed;

    @Column(length = 40, nullable = false)
    private String status = "pending_review";

    @Column(name = "reviewer_id", length = 128)
    private String reviewerId;

    @Column(name = "reviewer_decision", length = 40)
    private String reviewerDecision;

    @Column(name = "reviewer_comment", columnDefinition = "TEXT")
    private String reviewerComment;

    @Column(name = "replay_case_id", length = 128)
    private String replayCaseId;

    @Column(name = "replay_case_json", columnDefinition = "TEXT")
    private String replayCaseJson;

    @Column(name = "manifest_json", columnDefinition = "TEXT")
    private String manifestJson;

    @Column(name = "memory_before_json", columnDefinition = "TEXT")
    private String memoryBeforeJson;

    @Column(name = "source_context_json", columnDefinition = "TEXT")
    private String sourceContextJson;

    @Column(name = "policy_snapshot_json", columnDefinition = "TEXT")
    private String policySnapshotJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = "pending_review";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Long getMemoryId() { return memoryId; }
    public void setMemoryId(Long memoryId) { this.memoryId = memoryId; }

    public String getFeedbackType() { return feedbackType; }
    public void setFeedbackType(String feedbackType) { this.feedbackType = feedbackType; }

    public String getUserComment() { return userComment; }
    public void setUserComment(String userComment) { this.userComment = userComment; }

    public String getExpectedContent() { return expectedContent; }
    public void setExpectedContent(String expectedContent) { this.expectedContent = expectedContent; }

    public String getExpectedMemoryType() { return expectedMemoryType; }
    public void setExpectedMemoryType(String expectedMemoryType) { this.expectedMemoryType = expectedMemoryType; }

    public Boolean getExpectedCaptureAllowed() { return expectedCaptureAllowed; }
    public void setExpectedCaptureAllowed(Boolean expectedCaptureAllowed) { this.expectedCaptureAllowed = expectedCaptureAllowed; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReviewerId() { return reviewerId; }
    public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }

    public String getReviewerDecision() { return reviewerDecision; }
    public void setReviewerDecision(String reviewerDecision) { this.reviewerDecision = reviewerDecision; }

    public String getReviewerComment() { return reviewerComment; }
    public void setReviewerComment(String reviewerComment) { this.reviewerComment = reviewerComment; }

    public String getReplayCaseId() { return replayCaseId; }
    public void setReplayCaseId(String replayCaseId) { this.replayCaseId = replayCaseId; }

    public String getReplayCaseJson() { return replayCaseJson; }
    public void setReplayCaseJson(String replayCaseJson) { this.replayCaseJson = replayCaseJson; }

    public String getManifestJson() { return manifestJson; }
    public void setManifestJson(String manifestJson) { this.manifestJson = manifestJson; }

    public String getMemoryBeforeJson() { return memoryBeforeJson; }
    public void setMemoryBeforeJson(String memoryBeforeJson) { this.memoryBeforeJson = memoryBeforeJson; }

    public String getSourceContextJson() { return sourceContextJson; }
    public void setSourceContextJson(String sourceContextJson) { this.sourceContextJson = sourceContextJson; }

    public String getPolicySnapshotJson() { return policySnapshotJson; }
    public void setPolicySnapshotJson(String policySnapshotJson) { this.policySnapshotJson = policySnapshotJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
