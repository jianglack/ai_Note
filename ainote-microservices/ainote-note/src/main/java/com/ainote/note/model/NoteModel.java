package com.ainote.note.model;

import java.util.ArrayList;
import java.util.List;

public class NoteModel {
    private String id;
    private String title;
    private String content;
    private String folderId;
    private String createdAt;
    private String updatedAt;
    private String deletedAt;
    private boolean pinned;
    private boolean starred;
    private boolean archived;
    private List<TagModel> tags = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getFolderId() { return folderId; }
    public void setFolderId(String folderId) { this.folderId = folderId; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public String getDeletedAt() { return deletedAt; }
    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public boolean isStarred() { return starred; }
    public void setStarred(boolean starred) { this.starred = starred; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public List<TagModel> getTags() { return tags; }
    public void setTags(List<TagModel> tags) { this.tags = tags; }
}
