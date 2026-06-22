package com.ainote.app.model;

public class BatchArchiveRequest extends BatchNoteRequest {
    private boolean archive;

    public boolean isArchive() {
        return archive;
    }

    public void setArchive(boolean archive) {
        this.archive = archive;
    }
}
