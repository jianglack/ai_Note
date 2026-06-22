package com.ainote.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "note_concepts")
public class NoteConcept {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "note_id", length = 36, nullable = false)
    private String noteId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(length = 100, nullable = false)
    private String concept;

    @Column(length = 30, nullable = false)
    private String category = "keyword";

    @Column(nullable = false)
    private Double confidence = 0.8;

    @Column(name = "extracted_at", nullable = false)
    private LocalDateTime extractedAt;

    @PrePersist
    protected void onCreate() {
        if (extractedAt == null) extractedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNoteId() { return noteId; }
    public void setNoteId(String noteId) { this.noteId = noteId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getConcept() { return concept; }
    public void setConcept(String concept) { this.concept = concept; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public LocalDateTime getExtractedAt() { return extractedAt; }
    public void setExtractedAt(LocalDateTime extractedAt) { this.extractedAt = extractedAt; }
}
