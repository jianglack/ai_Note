package com.ainote.app.service;

import com.ainote.app.entity.Annotation;
import com.ainote.app.entity.Note;
import com.ainote.app.entity.Tag;
import com.ainote.app.repository.AnnotationRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.TagRepository;
import com.ainote.app.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AnnotationService {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final AnnotationRepository annotationRepository;
    private final NoteRepository noteRepository;
    private final TagRepository tagRepository;
    private final SecurityUtils securityUtils;
    
    public AnnotationService(AnnotationRepository annotationRepository,
                           NoteRepository noteRepository,
                           TagRepository tagRepository,
                           SecurityUtils securityUtils) {
        this.annotationRepository = annotationRepository;
        this.noteRepository = noteRepository;
        this.tagRepository = tagRepository;
        this.securityUtils = securityUtils;
    }
    
    @Transactional
    public com.ainote.app.model.Annotation create(String noteId, String textContent, String comment, 
                                                   Integer startOffset, Integer endOffset, List<String> tagNames) {
        String userId = securityUtils.getCurrentUserId();
        
        Note note = noteRepository.findById(noteId)
            .orElseThrow(() -> new RuntimeException("Note not found"));
        
        if (!note.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }
        
        Annotation annotation = new Annotation();
        annotation.setNote(note);
        annotation.setUserId(userId);
        annotation.setTextContent(textContent);
        annotation.setComment(comment);
        annotation.setStartOffset(startOffset);
        annotation.setEndOffset(endOffset);
        
        // 处理标签（标签直接添加到笔记上）
        if (tagNames != null && !tagNames.isEmpty()) {
            for (String tagName : tagNames) {
                Tag tag = tagRepository.findByNameAndUserId(tagName, userId)
                    .orElseGet(() -> {
                        Tag newTag = new Tag();
                        newTag.setId(java.util.UUID.randomUUID().toString());
                        newTag.setName(tagName);
                        newTag.setUserId(userId);
                        return tagRepository.save(newTag);
                    });

                // 将标签添加到笔记
                if (!note.getTags().contains(tag)) {
                    note.getTags().add(tag);
                }
            }
            noteRepository.save(note);
        }
        
        annotation = annotationRepository.save(annotation);
        return toModel(annotation);
    }
    
    public List<com.ainote.app.model.Annotation> getByNoteId(String noteId) {
        String userId = securityUtils.getCurrentUserId();
        
        Note note = noteRepository.findById(noteId)
            .orElseThrow(() -> new RuntimeException("Note not found"));
        
        if (!note.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }
        
        return annotationRepository.findByNoteIdOrderByStartOffsetAsc(noteId)
            .stream()
            .map(this::toModel)
            .collect(Collectors.toList());
    }
    
    @Transactional
    public com.ainote.app.model.Annotation update(String id, String comment, List<String> tagNames) {
        String userId = securityUtils.getCurrentUserId();
        
        Annotation annotation = annotationRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Annotation not found"));
        
        if (!annotation.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }
        
        if (comment != null) {
            annotation.setComment(comment);
        }
        
        // 更新标签
        if (tagNames != null) {
            Note note = annotation.getNote();

            for (String tagName : tagNames) {
                Tag tag = tagRepository.findByNameAndUserId(tagName, userId)
                    .orElseGet(() -> {
                        Tag newTag = new Tag();
                        newTag.setId(java.util.UUID.randomUUID().toString());
                        newTag.setName(tagName);
                        newTag.setUserId(userId);
                        return tagRepository.save(newTag);
                    });

                // 将标签添加到笔记
                if (!note.getTags().contains(tag)) {
                    note.getTags().add(tag);
                }
            }
            noteRepository.save(note);
        }
        
        annotation = annotationRepository.save(annotation);
        return toModel(annotation);
    }
    
    @Transactional
    public void delete(String id) {
        String userId = securityUtils.getCurrentUserId();
        
        Annotation annotation = annotationRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Annotation not found"));
        
        if (!annotation.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }
        
        annotationRepository.delete(annotation);
    }
    
    private com.ainote.app.model.Annotation toModel(Annotation entity) {
        com.ainote.app.model.Annotation model = new com.ainote.app.model.Annotation();
        model.setId(entity.getId());
        model.setNoteId(entity.getNote().getId());
        model.setTextContent(entity.getTextContent());
        model.setComment(entity.getComment());
        model.setStartOffset(entity.getStartOffset());
        model.setEndOffset(entity.getEndOffset());
        model.setCreatedAt(entity.getCreatedAt().format(FORMATTER));
        model.setUpdatedAt(entity.getUpdatedAt().format(FORMATTER));
        
        return model;
    }
}
