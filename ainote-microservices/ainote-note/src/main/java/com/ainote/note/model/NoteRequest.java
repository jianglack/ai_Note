package com.ainote.note.model;

import java.util.ArrayList;
import java.util.List;

public class NoteRequest {
    private String title;
    private String content;
    private String folderId;
    private Boolean pinned;
    private Boolean starred;
    private Boolean archived;
    private List<String> tags = new ArrayList<>();

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getFolderId() { return folderId; }
    public void setFolderId(String folderId) { this.folderId = folderId; }
    public Boolean getPinned() { return pinned; }
    public void setPinned(Boolean pinned) { this.pinned = pinned; }
    public Boolean getStarred() { return starred; }
    public void setStarred(Boolean starred) { this.starred = starred; }
    public Boolean getArchived() { return archived; }
    public void setArchived(Boolean archived) { this.archived = archived; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
