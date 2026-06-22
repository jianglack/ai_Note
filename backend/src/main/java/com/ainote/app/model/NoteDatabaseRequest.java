package com.ainote.app.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class NoteDatabaseRequest {

    public interface Create {}

    @NotBlank(groups = Create.class, message = "Note id is required")
    @Size(max = 64, message = "Note id must be at most 64 characters")
    private String noteId;

    @Size(max = 200, message = "Database name must be at most 200 characters")
    private String name;

    @Size(max = 200000, message = "Columns payload is too large")
    private String columns;

    @Size(max = 200000, message = "View config payload is too large")
    private String viewConfig;

    public String getNoteId() {
        return noteId;
    }

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColumns() {
        return columns;
    }

    public void setColumns(String columns) {
        this.columns = columns;
    }

    public String getViewConfig() {
        return viewConfig;
    }

    public void setViewConfig(String viewConfig) {
        this.viewConfig = viewConfig;
    }
}
