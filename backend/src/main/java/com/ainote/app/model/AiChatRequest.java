package com.ainote.app.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class AiChatRequest {

    @Size(max = 8000, message = "Message must be at most 8000 characters")
    private String message;

    @Size(max = 8000, message = "Query must be at most 8000 characters")
    private String query;

    @Pattern(regexp = "all|selected", message = "Scope must be all or selected")
    private String scope;

    @Size(max = 64, message = "Note id must be at most 64 characters")
    private String noteId;

    @Size(max = 100, message = "At most 100 note ids are allowed")
    private List<@NotBlank(message = "Note id must not be blank") @Size(max = 64, message = "Note id must be at most 64 characters") String> noteIds = new ArrayList<>();

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
        if (noteId != null && !noteId.isBlank()) {
            List<String> ids = new ArrayList<>();
            ids.add(noteId);
            return ids;
        }
        return noteIds;
    }

    public void setNoteIds(List<String> noteIds) {
        this.noteIds = noteIds;
    }

    @AssertTrue(message = "Message or query is required")
    public boolean isPromptPresent() {
        return hasText(message) || hasText(query);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
