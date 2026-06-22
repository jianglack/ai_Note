package com.ainote.app.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class TypedLinkRequest {

    @NotBlank(message = "Source note id is required")
    @Size(max = 64, message = "Source note id must be at most 64 characters")
    private String sourceNoteId;

    @NotBlank(message = "Target note id is required")
    @Size(max = 64, message = "Target note id must be at most 64 characters")
    private String targetNoteId;

    @Size(max = 50, message = "Relation type must be at most 50 characters")
    private String relationType;

    @Size(max = 50, message = "Link type must be at most 50 characters")
    private String linkType;

    @Size(max = 5000, message = "Context must be at most 5000 characters")
    private String context;

    public String getSourceNoteId() {
        return sourceNoteId;
    }

    public void setSourceNoteId(String sourceNoteId) {
        this.sourceNoteId = sourceNoteId;
    }

    public String getTargetNoteId() {
        return targetNoteId;
    }

    public void setTargetNoteId(String targetNoteId) {
        this.targetNoteId = targetNoteId;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public String getLinkType() {
        return linkType;
    }

    public void setLinkType(String linkType) {
        this.linkType = linkType;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }
}
