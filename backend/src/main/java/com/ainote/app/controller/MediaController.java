package com.ainote.app.controller;

import com.ainote.app.entity.NoteMedia;
import com.ainote.app.model.MediaTableRequest;
import com.ainote.app.service.MediaService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("noteId") String noteId,
            @RequestParam(value = "alt", required = false) String alt) throws IOException {

        NoteMedia media = mediaService.uploadImage(noteId, file, alt);
        return ResponseEntity.ok(Map.of(
            "id", media.getId(),
            "url", "/api/media/" + media.getId(),
            "filename", media.getFilename() != null ? media.getFilename() : "",
            "mimeType", media.getMimeType() != null ? media.getMimeType() : ""
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> getImage(@PathVariable String id) {
        NoteMedia media = mediaService.getById(id);
        byte[] data = mediaService.getImageBytes(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                    media.getMimeType() != null ? media.getMimeType() : "application/octet-stream"))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)))
                .body(data);
    }

    @GetMapping("/{id}/meta")
    public ResponseEntity<Map<String, Object>> getMediaMeta(@PathVariable String id) {
        NoteMedia media = mediaService.getById(id);
        return ResponseEntity.ok(Map.of(
            "id", media.getId(),
            "noteId", media.getNoteId(),
            "mediaType", media.getMediaType(),
            "filename", media.getFilename() != null ? media.getFilename() : "",
            "ocrText", media.getOcrText() != null ? media.getOcrText() : "",
            "metadata", media.getMetadata() != null ? media.getMetadata() : "{}",
            "createdAt", media.getCreatedAt().toString()
        ));
    }

    @PostMapping("/table")
    public ResponseEntity<Map<String, Object>> saveTable(@Valid @RequestBody MediaTableRequest request) {
        NoteMedia media = mediaService.saveTable(
            request.getNoteId(),
            request.getTableHtml(),
            request.getTableJson(),
            request.getCaption()
        );
        return ResponseEntity.ok(Map.of(
            "id", media.getId(),
            "tableMarkdown", media.getTableMarkdown() != null ? media.getTableMarkdown() : "",
            "indexed", true
        ));
    }

    @GetMapping("/note/{noteId}")
    public ResponseEntity<List<Map<String, Object>>> getByNote(@PathVariable String noteId) {
        List<NoteMedia> mediaList = mediaService.getByNoteId(noteId);
        List<Map<String, Object>> result = mediaList.stream().map(m -> Map.<String, Object>of(
            "id", m.getId(),
            "mediaType", m.getMediaType(),
            "filename", m.getFilename() != null ? m.getFilename() : "",
            "mimeType", m.getMimeType() != null ? m.getMimeType() : "",
            "ocrText", m.getOcrText() != null ? m.getOcrText() : "",
            "fileSizeBytes", m.getFileSizeBytes() != null ? m.getFileSizeBytes() : 0,
            "createdAt", m.getCreatedAt().toString()
        )).toList();
        return ResponseEntity.ok(result);
    }
}
