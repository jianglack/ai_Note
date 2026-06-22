package com.ainote.app.model;

/**
 * AI 智能分类建议
 */
public class ClassificationSuggestion {
    private String noteId;
    private String noteTitle;
    private String suggestedFolderId;
    private String suggestedFolderName;
    private String reason;
    private boolean isNewFolder;  // 是否需要新建文件夹

    public ClassificationSuggestion() {}

    public ClassificationSuggestion(String noteId, String noteTitle, String suggestedFolderId,
                                    String suggestedFolderName, String reason, boolean isNewFolder) {
        this.noteId = noteId;
        this.noteTitle = noteTitle;
        this.suggestedFolderId = suggestedFolderId;
        this.suggestedFolderName = suggestedFolderName;
        this.reason = reason;
        this.isNewFolder = isNewFolder;
    }

    // Getters and Setters
    public String getNoteId() { return noteId; }
    public void setNoteId(String noteId) { this.noteId = noteId; }

    public String getNoteTitle() { return noteTitle; }
    public void setNoteTitle(String noteTitle) { this.noteTitle = noteTitle; }

    public String getSuggestedFolderId() { return suggestedFolderId; }
    public void setSuggestedFolderId(String suggestedFolderId) { this.suggestedFolderId = suggestedFolderId; }

    public String getSuggestedFolderName() { return suggestedFolderName; }
    public void setSuggestedFolderName(String suggestedFolderName) { this.suggestedFolderName = suggestedFolderName; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isNewFolder() { return isNewFolder; }
    public void setNewFolder(boolean newFolder) { isNewFolder = newFolder; }
}
