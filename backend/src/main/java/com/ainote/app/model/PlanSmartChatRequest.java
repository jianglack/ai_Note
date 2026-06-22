package com.ainote.app.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class PlanSmartChatRequest {

    @NotBlank(message = "Query is required")
    @Size(max = 8000, message = "Query must be at most 8000 characters")
    private String query;

    @Size(max = 100, message = "At most 100 note ids are allowed")
    private List<@NotBlank(message = "Note id must not be blank") @Size(max = 64, message = "Note id must be at most 64 characters") String> noteIds = new ArrayList<>();

    @NotNull(message = "forcePlan is required")
    private Boolean forcePlan = false;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<String> getNoteIds() {
        return noteIds != null ? noteIds : List.of();
    }

    public void setNoteIds(List<String> noteIds) {
        this.noteIds = noteIds;
    }

    public Boolean getForcePlan() {
        return forcePlan;
    }

    public void setForcePlan(Boolean forcePlan) {
        this.forcePlan = forcePlan;
    }
}
