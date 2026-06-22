package com.ainote.app.model;

import java.util.ArrayList;
import java.util.List;

public class TagAssignRequest {
    private String noteId;
    private List<String> tagIds = new ArrayList<>();

    public String getNoteId() {
        return noteId;
    }

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    public List<String> getTagIds() {
        return tagIds;
    }

    public void setTagIds(List<String> tagIds) {
        this.tagIds = tagIds;
    }
}

