package com.ainote.ai.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "note-service", fallback = NoteClientFallback.class)
public interface NoteClient {

    @GetMapping("/api/notes/{id}")
    Map<String, Object> getNote(@PathVariable("id") String id);

    @GetMapping("/api/notes")
    Map<String, Object> listNotes(@RequestParam("page") int page, @RequestParam("size") int size);

    @PostMapping("/api/notes")
    Map<String, Object> createNote(@RequestBody Map<String, Object> request);

    @PutMapping("/api/notes/{id}")
    Map<String, Object> updateNote(@PathVariable("id") String id, @RequestBody Map<String, Object> request);

    @DeleteMapping("/api/notes/{id}")
    void deleteNote(@PathVariable("id") String id);

    @PostMapping("/api/notes/{id}/restore")
    void restoreNote(@PathVariable("id") String id);

    @DeleteMapping("/api/notes/{id}/permanent")
    void permanentDeleteNote(@PathVariable("id") String id);

    @PostMapping("/api/notes/{id}/move")
    Map<String, Object> moveNote(@PathVariable("id") String id, @RequestBody Map<String, Object> request);

    @PostMapping("/api/notes/{id}/copy")
    Map<String, Object> copyNote(@PathVariable("id") String id);

    @PostMapping("/api/notes/merge")
    Map<String, Object> mergeNotes(@RequestBody Map<String, Object> request);

    @DeleteMapping("/api/notes/trash")
    Map<String, Object> emptyTrash();

    @GetMapping("/api/notes/trash")
    List<Map<String, Object>> listTrash();

    @GetMapping("/api/notes/search")
    List<Map<String, Object>> searchNotes(@RequestParam("q") String query);

    @GetMapping("/api/folders")
    List<Map<String, Object>> listFolders();

    @PostMapping("/api/folders")
    Map<String, Object> createFolder(@RequestBody Map<String, Object> request);

    @PutMapping("/api/folders/{id}")
    Map<String, Object> updateFolder(@PathVariable("id") String id, @RequestBody Map<String, Object> request);

    @DeleteMapping("/api/folders/{id}")
    void deleteFolder(@PathVariable("id") String id);

    @PostMapping("/api/notes/{noteId}/tags")
    void addTag(@PathVariable("noteId") String noteId, @RequestBody Map<String, Object> request);

    @DeleteMapping("/api/notes/{noteId}/tags/{tagName}")
    void removeTag(@PathVariable("noteId") String noteId, @PathVariable("tagName") String tagName);
}
