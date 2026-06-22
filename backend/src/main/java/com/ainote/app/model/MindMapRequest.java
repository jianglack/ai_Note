package com.ainote.app.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public class MindMapRequest {

    @Size(max = 200, message = "Mind map title must be at most 200 characters")
    private String title;

    @Size(max = 200000, message = "Mind map data is too large")
    private String data;

    @Size(max = 64, message = "Note id must be at most 64 characters")
    private String noteId;

    @Size(max = 50, message = "Source must be at most 50 characters")
    private String source;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getNoteId() {
        return noteId;
    }

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    @AssertTrue(message = "Mind map title must not be blank when supplied")
    public boolean isValidTitle() {
        return title == null || !title.isBlank();
    }
}
