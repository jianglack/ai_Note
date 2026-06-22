package com.ainote.app.model;

import java.util.List;

/**
 * AI 智能分类响应
 */
public class ClassificationResponse {
    private List<ClassificationSuggestion> suggestions;
    private List<String> newFolders;  // 需要新建的文件夹名称列表
    private String summary;  // AI 分类总结

    public ClassificationResponse() {}

    public ClassificationResponse(List<ClassificationSuggestion> suggestions,
                                  List<String> newFolders, String summary) {
        this.suggestions = suggestions;
        this.newFolders = newFolders;
        this.summary = summary;
    }

    public List<ClassificationSuggestion> getSuggestions() { return suggestions; }
    public void setSuggestions(List<ClassificationSuggestion> suggestions) { this.suggestions = suggestions; }

    public List<String> getNewFolders() { return newFolders; }
    public void setNewFolders(List<String> newFolders) { this.newFolders = newFolders; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
