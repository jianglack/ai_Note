package com.ainote.app.controller;

import com.ainote.app.model.Annotation;
import com.ainote.app.service.AnnotationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/annotations")
@Tag(name = "Annotations", description = "注释管理 API")
public class AnnotationController {
    
    private final AnnotationService annotationService;
    
    public AnnotationController(AnnotationService annotationService) {
        this.annotationService = annotationService;
    }
    
    @PostMapping
    @Operation(summary = "创建注释", description = "为笔记中的选中文字创建注释")
    public Annotation createAnnotation(@Valid @RequestBody CreateAnnotationRequest request) {
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
    @Operation(summary = "获取笔记的所有注释", description = "按位置顺序返回")
    public List<Annotation> getAnnotationsByNote(@PathVariable String noteId) {
        return annotationService.getByNoteId(noteId);
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "更新注释", description = "更新注释内容和标签")
    public Annotation updateAnnotation(@PathVariable String id, @Valid @RequestBody UpdateAnnotationRequest request) {
        return annotationService.update(id, request.getComment(), request.getTags());
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "删除注释", description = "删除指定的注释")
    public void deleteAnnotation(@PathVariable String id) {
        annotationService.delete(id);
    }
    
    // Request DTOs
    public static class CreateAnnotationRequest {
        @NotBlank(message = "Note id is required")
        @Size(max = 64, message = "Note id must be at most 64 characters")
        private String noteId;
        @NotBlank(message = "Selected text is required")
        @Size(max = 20000, message = "Selected text must be at most 20000 characters")
        private String textContent;
        @Size(max = 10000, message = "Comment must be at most 10000 characters")
        private String comment;
        @Min(value = 0, message = "Start offset must be non-negative")
        private Integer startOffset;
        @Min(value = 0, message = "End offset must be non-negative")
        private Integer endOffset;
        @Size(max = 50, message = "At most 50 tags are allowed")
        private List<@NotBlank(message = "Tag must not be blank") @Size(max = 50, message = "Tag must be at most 50 characters") String> tags;
        
        public String getNoteId() {
            return noteId;
        }
        
        public void setNoteId(String noteId) {
            this.noteId = noteId;
        }
        
        public String getTextContent() {
            return textContent;
        }
        
        public void setTextContent(String textContent) {
            this.textContent = textContent;
        }
        
        public String getComment() {
            return comment;
        }
        
        public void setComment(String comment) {
            this.comment = comment;
        }
        
        public Integer getStartOffset() {
            return startOffset;
        }
        
        public void setStartOffset(Integer startOffset) {
            this.startOffset = startOffset;
        }
        
        public Integer getEndOffset() {
            return endOffset;
        }
        
        public void setEndOffset(Integer endOffset) {
            this.endOffset = endOffset;
        }
        
        public List<String> getTags() {
            return tags;
        }
        
        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }
    
    public static class UpdateAnnotationRequest {
        @Size(max = 10000, message = "Comment must be at most 10000 characters")
        private String comment;
        @Size(max = 50, message = "At most 50 tags are allowed")
        private List<@NotBlank(message = "Tag must not be blank") @Size(max = 50, message = "Tag must be at most 50 characters") String> tags;
        
        public String getComment() {
            return comment;
        }
        
        public void setComment(String comment) {
            this.comment = comment;
        }
        
        public List<String> getTags() {
            return tags;
        }
        
        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }
}
