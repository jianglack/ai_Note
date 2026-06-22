package com.ainote.ai.model;

import com.ainote.ai.agent.pipeline.ToolAuditLogger;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiChatResponse {
    private String content;
    private Map<Integer, NoteSource> sources;
    private String action;
    private List<ToolCall> toolCalls;
    private List<ToolAuditLogger.ToolAuditEntry> transcript;

    public AiChatResponse() {}

    public AiChatResponse(String content, Map<Integer, NoteSource> sources) {
        this.content = content;
        this.sources = sources;
    }

    public AiChatResponse(String content, Map<Integer, NoteSource> sources, String action) {
        this.content = content;
        this.sources = sources;
        this.action = action;
    }

    public AiChatResponse(String content, Map<Integer, NoteSource> sources, List<ToolCall> toolCalls) {
        this.content = content;
        this.sources = sources;
        this.toolCalls = toolCalls;
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Map<Integer, NoteSource> getSources() { return sources; }
    public void setSources(Map<Integer, NoteSource> sources) { this.sources = sources; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public List<ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCall> toolCalls) { this.toolCalls = toolCalls; }

    public List<ToolAuditLogger.ToolAuditEntry> getTranscript() { return transcript; }
    public void setTranscript(List<ToolAuditLogger.ToolAuditEntry> transcript) { this.transcript = transcript; }

    public static class NoteSource {
        private String id;
        private String title;

        public NoteSource() {}

        public NoteSource(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }
}
