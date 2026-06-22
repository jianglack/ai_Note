package com.ainote.ai.model;

import java.util.ArrayList;
import java.util.List;

public class AiChatRequest {
    private String message;
    private String query;
    private String scope;
    private String noteId;
    private List<String> noteIds = new ArrayList<>();

    public String getMessage() {
        return message != null ? message : query;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getQuery() {
        return query != null ? query : message;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getNoteId() {
        return noteId;
    }

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    public List<String> getNoteIds() {
        if (noteId != null && !noteId.isEmpty()) {
            List<String> ids = new ArrayList<>();
            ids.add(noteId);
            return ids;
        }
        return noteIds;
    }

    public void setNoteIds(List<String> noteIds) {
        this.noteIds = noteIds;
    }
}
