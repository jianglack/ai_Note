package com.ainote.note.service;

import com.ainote.common.security.UserContext;
import com.ainote.note.entity.Annotation;
import com.ainote.note.entity.Note;
import com.ainote.note.entity.Tag;
import com.ainote.note.model.AnnotationModel;
import com.ainote.note.repository.AnnotationRepository;
import com.ainote.note.repository.NoteRepository;
import com.ainote.note.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AnnotationService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AnnotationRepository annotationRepository;
    private final NoteRepository noteRepository;
    private final TagRepository tagRepository;

    public AnnotationService(AnnotationRepository annotationRepository,
                             NoteRepository noteRepository,
                             TagRepository tagRepository) {
        this.annotationRepository = annotationRepository;
        this.noteRepository = noteRepository;
        this.tagRepository = tagRepository;
    }

    private String getCurrentUserId() {
        return String.valueOf(UserContext.getCurrentUserId());
    }

    @Transactional
    public AnnotationModel create(String noteId, String textContent, String comment,
                                  Integer startOffset, Integer endOffset, List<String> tagNames) {
        String userId = getCurrentUserId();

        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note not found"));

        if (!note.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        Annotation annotation = new Annotation();
        annotation.setNote(note);
        annotation.setUserId(userId);
        annotation.setTextContent(textContent);
        annotation.setComment(comment);
        annotation.setStartOffset(startOffset);
        annotation.setEndOffset(endOffset);

        // Handle tags (add to note)
        if (tagNames != null && !tagNames.isEmpty()) {
            for (String tagName : tagNames) {
                Tag tag = tagRepository.findByName(tagName)
                        .orElseGet(() -> {
                            Tag newTag = new Tag();
                            newTag.setId(UUID.randomUUID().toString());
                            newTag.setName(tagName);
                            return tagRepository.save(newTag);
                        });

                if (!note.getTags().contains(tag)) {
                    note.getTags().add(tag);
                }
            }
            noteRepository.save(note);
        }

        annotation = annotationRepository.save(annotation);
        return toModel(annotation);
    }

    @Transactional(readOnly = true)
    public List<AnnotationModel> getByNoteId(String noteId) {
        String userId = getCurrentUserId();

        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note not found"));

        if (!note.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        return annotationRepository.findByNoteIdOrderByStartOffsetAsc(noteId)
                .stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    @Transactional
    public AnnotationModel update(String id, String comment, List<String> tagNames) {
        String userId = getCurrentUserId();

        Annotation annotation = annotationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Annotation not found"));

        if (!annotation.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        if (comment != null) {
            annotation.setComment(comment);
        }

        if (tagNames != null) {
            Note note = annotation.getNote();
            for (String tagName : tagNames) {
                Tag tag = tagRepository.findByName(tagName)
                        .orElseGet(() -> {
                            Tag newTag = new Tag();
                            newTag.setId(UUID.randomUUID().toString());
                            newTag.setName(tagName);
                            return tagRepository.save(newTag);
                        });

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
        String userId = getCurrentUserId();

        Annotation annotation = annotationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Annotation not found"));

        if (!annotation.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        annotationRepository.delete(annotation);
    }

    private AnnotationModel toModel(Annotation entity) {
        AnnotationModel model = new AnnotationModel();
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
