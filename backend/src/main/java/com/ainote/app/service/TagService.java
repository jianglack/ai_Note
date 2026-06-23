package com.ainote.app.service;

import com.ainote.app.entity.Note;
import com.ainote.app.entity.Tag;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.TagRepository;
import com.ainote.app.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TagService {
    
    private final TagRepository tagRepository;
    private final NoteRepository noteRepository;
    private final KnowledgeGraphService knowledgeGraphService;
    private final SecurityUtils securityUtils;
    
    public TagService(TagRepository tagRepository, NoteRepository noteRepository,
                      KnowledgeGraphService knowledgeGraphService,
                      SecurityUtils securityUtils) {
        this.tagRepository = tagRepository;
        this.noteRepository = noteRepository;
        this.knowledgeGraphService = knowledgeGraphService;
        this.securityUtils = securityUtils;
    }
    
    public com.ainote.app.model.Tag create(String name) {
        String userId = securityUtils.getCurrentUserId();
        Tag tag = tagRepository.findByNameAndUserId(name, userId)
                .orElseGet(() -> {
                    Tag newTag = new Tag();
                    newTag.setId(UUID.randomUUID().toString());
                    newTag.setName(name);
                    newTag.setUserId(userId);
                    return tagRepository.save(newTag);
                });
        return toModel(tag);
    }
    
    public List<com.ainote.app.model.Tag> listAll() {
        String userId = securityUtils.getCurrentUserId();
        return tagRepository.findAllByUserId(userId).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }
    
    public void delete(String id) {
        String userId = securityUtils.getCurrentUserId();
        tagRepository.findByIdAndUserId(id, userId).ifPresent(tag -> {
            tagRepository.delete(tag);
            afterCommit(() -> knowledgeGraphService.deleteTag(id));
        });
    }
    
    public void assign(String noteId, List<String> tagIds) {
        String currentUserId = securityUtils.getCurrentUserId();
        noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, currentUserId).ifPresent(note -> {
            List<Tag> tags = (tagIds != null) ? tagIds.stream()
                    .map(tagId -> tagRepository.findByIdAndUserId(tagId, currentUserId).orElse(null))
                    .filter(tag -> tag != null)
                    .collect(Collectors.toList()) : List.of();
            note.getTags().clear();
            note.getTags().addAll(tags);
            noteRepository.save(note);
            afterCommit(() -> knowledgeGraphService.syncNote(noteId));
        });
    }

    private com.ainote.app.model.Tag toModel(Tag tag) {
        com.ainote.app.model.Tag model = new com.ainote.app.model.Tag();
        model.setId(tag.getId());
        model.setName(tag.getName());
        return model;
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
