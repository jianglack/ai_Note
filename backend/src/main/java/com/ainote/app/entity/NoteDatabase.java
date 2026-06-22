package com.ainote.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "note_databases")
public class NoteDatabase {
    @Id
    private String id;
    @Column(name = "note_id", nullable = false)
    private String noteId;
    @Column(name = "user_id", nullable = false)
    private String userId;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, columnDefinition = "jsonb")
    private String columns;
    @Column(name = "view_config", columnDefinition = "jsonb")
    private String viewConfig;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNoteId() { return noteId; }
    public void setNoteId(String noteId) { this.noteId = noteId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColumns() { return columns; }
    public void setColumns(String columns) { this.columns = columns; }
    public String getViewConfig() { return viewConfig; }
    public void setViewConfig(String viewConfig) { this.viewConfig = viewConfig; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t) { this.updatedAt = t; }

    @PrePersist
    protected void onCreate() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (viewConfig == null) viewConfig = "{}";
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
