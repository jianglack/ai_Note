package com.ainote.app.model;

import jakarta.validation.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;

public class BatchNoteRequest {
    @NotEmpty
    private List<String> noteIds = new ArrayList<>();

    public List<String> getNoteIds() {
        return noteIds;
    }

    public void setNoteIds(List<String> noteIds) {
        this.noteIds = noteIds;
    }
}
