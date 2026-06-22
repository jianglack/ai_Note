package com.ainote.app.model;

import java.util.ArrayList;
import java.util.List;

public class BatchOperationResult {
    private int successCount;
    private int failedCount;
    private List<Failure> failed = new ArrayList<>();
    private List<Detail> details = new ArrayList<>();

    public void addSuccess() {
        successCount++;
    }

    public void addSuccess(String noteId, String title) {
        successCount++;
        details.add(new Detail(noteId, title, "success", null));
    }

    public void addFailure(String noteId, String reason) {
        failed.add(new Failure(noteId, reason));
        failedCount = failed.size();
    }

    public void addFailure(String noteId, String title, String reason) {
        failed.add(new Failure(noteId, reason));
        failedCount = failed.size();
        details.add(new Detail(noteId, title, "failed", reason));
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public List<Failure> getFailed() {
        return failed;
    }

    public void setFailed(List<Failure> failed) {
        this.failed = failed;
        this.failedCount = failed != null ? failed.size() : 0;
    }

    public List<Detail> getDetails() {
        return details;
    }

    public void setDetails(List<Detail> details) {
        this.details = details;
    }

    public static class Detail {
        private String noteId;
        private String title;
        private String status; // "success" or "failed"
        private String error;

        public Detail() {}

        public Detail(String noteId, String title, String status, String error) {
            this.noteId = noteId;
            this.title = title;
            this.status = status;
            this.error = error;
        }

        public String getNoteId() { return noteId; }
        public void setNoteId(String noteId) { this.noteId = noteId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    public static class Failure {
        private String noteId;
        private String reason;

        public Failure() {
        }

        public Failure(String noteId, String reason) {
            this.noteId = noteId;
            this.reason = reason;
        }

        public String getNoteId() {
            return noteId;
        }

        public void setNoteId(String noteId) {
            this.noteId = noteId;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
