package com.ainote.app.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MediaTableRequest {

    @NotBlank(message = "Note id is required")
    @Size(max = 64, message = "Note id must be at most 64 characters")
    private String noteId;

    @Size(max = 200000, message = "Table HTML is too large")
    private String tableHtml;

    @Size(max = 200000, message = "Table JSON is too large")
    private String tableJson;

    @Size(max = 1000, message = "Caption must be at most 1000 characters")
    private String caption;

    public String getNoteId() {
        return noteId;
    }

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    public String getTableHtml() {
        return tableHtml;
    }

    public void setTableHtml(String tableHtml) {
        this.tableHtml = tableHtml;
    }

    public String getTableJson() {
        return tableJson;
    }

    public void setTableJson(String tableJson) {
        this.tableJson = tableJson;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    @AssertTrue(message = "Table HTML or table JSON is required")
    public boolean isTablePayloadPresent() {
        return hasText(tableHtml) || hasText(tableJson);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
