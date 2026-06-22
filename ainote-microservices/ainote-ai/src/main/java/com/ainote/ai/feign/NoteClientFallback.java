package com.ainote.ai.feign;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class NoteClientFallback implements NoteClient {

    @Override
    public Map<String, Object> getNote(String id) {
        return Map.of("error", "note-service unavailable");
    }

    @Override
    public Map<String, Object> listNotes(int page, int size) {
        return Map.of("content", List.of());
    }

    @Override
    public Map<String, Object> createNote(Map<String, Object> request) {
        return Map.of("error", "note-service unavailable");
    }

    @Override
    public Map<String, Object> updateNote(String id, Map<String, Object> request) {
        return Map.of("error", "note-service unavailable");
    }

    @Override
    public void deleteNote(String id) { }

    @Override
    public void restoreNote(String id) { }

    @Override
    public void permanentDeleteNote(String id) { }

    @Override
    public Map<String, Object> moveNote(String id, Map<String, Object> request) {
        return Map.of("error", "note-service unavailable");
    }

    @Override
    public Map<String, Object> copyNote(String id) {
        return Map.of("error", "note-service unavailable");
    }

    @Override
    public Map<String, Object> mergeNotes(Map<String, Object> request) {
        return Map.of("error", "note-service unavailable");
    }

    @Override
    public Map<String, Object> emptyTrash() {
        return Map.of("error", "note-service unavailable");
    }

    @Override
    public List<Map<String, Object>> listTrash() {
        return List.of();
    }

    @Override
    public List<Map<String, Object>> searchNotes(String query) {
        return List.of();
    }

    @Override
    public List<Map<String, Object>> listFolders() {
        return List.of();
    }

    @Override
    public Map<String, Object> createFolder(Map<String, Object> request) {
        return Map.of("error", "note-service unavailable");
    }

    @Override
    public Map<String, Object> updateFolder(String id, Map<String, Object> request) {
        return Map.of("error", "note-service unavailable");
    }

    @Override
    public void deleteFolder(String id) { }

    @Override
    public void addTag(String noteId, Map<String, Object> request) { }

    @Override
    public void removeTag(String noteId, String tagName) { }
}
