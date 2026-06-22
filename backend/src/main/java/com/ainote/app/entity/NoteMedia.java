package com.ainote.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "note_media")
public class NoteMedia {
    @Id
    private String id;

    @Column(name = "note_id", nullable = false)
    private String noteId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "media_type", nullable = false)
    private String mediaType;

    private String filename;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "data_base64", nullable = false, columnDefinition = "TEXT")
    private String dataBase64;

    @Column(name = "ocr_text", columnDefinition = "TEXT")
    private String ocrText;

    @Column(name = "table_json", columnDefinition = "jsonb")
    private String tableJson;

    @Column(name = "table_markdown", columnDefinition = "TEXT")
    private String tableMarkdown;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "file_size_bytes")
    private Integer fileSizeBytes;

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
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getDataBase64() { return dataBase64; }
    public void setDataBase64(String dataBase64) { this.dataBase64 = dataBase64; }
    public String getOcrText() { return ocrText; }
    public void setOcrText(String ocrText) { this.ocrText = ocrText; }
    public String getTableJson() { return tableJson; }
    public void setTableJson(String tableJson) { this.tableJson = tableJson; }
    public String getTableMarkdown() { return tableMarkdown; }
    public void setTableMarkdown(String tableMarkdown) { this.tableMarkdown = tableMarkdown; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public Integer getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Integer fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PrePersist
    protected void onCreate() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
