package com.ainote.app.model;

public class BatchFlagRequest extends BatchNoteRequest {
    private boolean value;

    public boolean isValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }
}
