package com.ainote.app.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AiNoteRequest {
    @NotBlank(message = "Note id is required")
    @Size(max = 64, message = "Note id must be at most 64 characters")
    private String noteId;

    public String getNoteId() {
        return noteId;
    }

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }
}

