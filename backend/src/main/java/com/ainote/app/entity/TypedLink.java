package com.ainote.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "typed_links")
public class TypedLink {
    @Id
    private String id;
    @Column(name = "source_note_id", nullable = false)
    private String sourceNoteId;
    @Column(name = "target_note_id", nullable = false)
    private String targetNoteId;
    @Column(name = "relation_type", nullable = false)
    private String relationType;
    private String context;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSourceNoteId() { return sourceNoteId; }
    public void setSourceNoteId(String sourceNoteId) { this.sourceNoteId = sourceNoteId; }
    public String getTargetNoteId() { return targetNoteId; }
    public void setTargetNoteId(String targetNoteId) { this.targetNoteId = targetNoteId; }
    public String getRelationType() { return relationType; }
    public void setRelationType(String relationType) { this.relationType = relationType; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }

    @PrePersist
    protected void onCreate() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        createdAt = LocalDateTime.now();
    }
}
