package com.ainote.app.model;

import jakarta.validation.constraints.NotBlank;

public class BatchTagRequest extends BatchNoteRequest {
    @NotBlank
    private String tagName;

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }
}
