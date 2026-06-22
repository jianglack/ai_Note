package com.ainote.note.controller;

import com.ainote.note.model.AnnotationModel;
import com.ainote.note.service.AnnotationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/annotations")
public class AnnotationController {

    private final AnnotationService annotationService;

    public AnnotationController(AnnotationService annotationService) {
        this.annotationService = annotationService;
    }

    @PostMapping
    public AnnotationModel createAnnotation(@RequestBody CreateAnnotationRequest request) {
        return annotationService.create(
                request.getNoteId(),
                request.getTextContent(),
                request.getComment(),
                request.getStartOffset(),
                request.getEndOffset(),
                request.getTags()
        );
    }

    @GetMapping("/note/{noteId}")
    public List<AnnotationModel> getAnnotationsByNote(@PathVariable String noteId) {
        return annotationService.getByNoteId(noteId);
    }

    @PutMapping("/{id}")
    public AnnotationModel updateAnnotation(@PathVariable String id, @RequestBody UpdateAnnotationRequest request) {
        return annotationService.update(id, request.getComment(), request.getTags());
    }

    @DeleteMapping("/{id}")
    public void deleteAnnotation(@PathVariable String id) {
        annotationService.delete(id);
    }

    // Request DTOs
    public static class CreateAnnotationRequest {
        private String noteId;
        private String textContent;
        private String comment;
        private Integer startOffset;
        private Integer endOffset;
        private List<String> tags;

        public String getNoteId() { return noteId; }
        public void setNoteId(String noteId) { this.noteId = noteId; }
        public String getTextContent() { return textContent; }
        public void setTextContent(String textContent) { this.textContent = textContent; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public Integer getStartOffset() { return startOffset; }
        public void setStartOffset(Integer startOffset) { this.startOffset = startOffset; }
        public Integer getEndOffset() { return endOffset; }
        public void setEndOffset(Integer endOffset) { this.endOffset = endOffset; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
    }

    public static class UpdateAnnotationRequest {
        private String comment;
        private List<String> tags;

        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
    }
}
