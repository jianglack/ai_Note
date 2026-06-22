package com.ainote.app.controller;

import com.ainote.app.model.Annotation;
import com.ainote.app.service.AnnotationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    public Annotation createAnnotation(@RequestBody CreateAnnotationRequest request) {
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
    public Annotation updateAnnotation(@PathVariable String id, @RequestBody UpdateAnnotationRequest request) {
        return annotationService.update(id, request.getComment(), request.getTags());
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "删除注释", description = "删除指定的注释")
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
        private String comment;
        private List<String> tags;
        
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
