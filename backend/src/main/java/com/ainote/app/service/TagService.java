package com.ainote.app.service;

import com.ainote.app.entity.Note;
import com.ainote.app.entity.Tag;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.TagRepository;
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
    
    public TagService(TagRepository tagRepository, NoteRepository noteRepository,
                      KnowledgeGraphService knowledgeGraphService) {
        this.tagRepository = tagRepository;
        this.noteRepository = noteRepository;
        this.knowledgeGraphService = knowledgeGraphService;
    }
    
    public com.ainote.app.model.Tag create(String name) {
        Tag tag = new Tag();
        tag.setId(UUID.randomUUID().toString());
        tag.setName(name);
        Tag saved = tagRepository.save(tag);
        
        com.ainote.app.model.Tag model = new com.ainote.app.model.Tag();
        model.setId(saved.getId());
        model.setName(saved.getName());
        return model;
    }
    
    public List<com.ainote.app.model.Tag> listAll() {
        return tagRepository.findAll().stream()
                .map(tag -> {
                    com.ainote.app.model.Tag model = new com.ainote.app.model.Tag();
                    model.setId(tag.getId());
                    model.setName(tag.getName());
                    return model;
                })
                .collect(Collectors.toList());
    }
    
    public void delete(String id) {
        tagRepository.deleteById(id);
        afterCommit(() -> knowledgeGraphService.deleteTag(id));
    }
    
    public void assign(String noteId, List<String> tagIds) {
        noteRepository.findById(noteId).ifPresent(note -> {
            List<Tag> tags = (tagIds != null) ? tagIds.stream()
                    .map(tagId -> tagRepository.findById(tagId).orElse(null))
                    .filter(tag -> tag != null)
                    .collect(Collectors.toList()) : List.of();
            note.getTags().clear();
            note.getTags().addAll(tags);
            noteRepository.save(note);
            afterCommit(() -> knowledgeGraphService.syncNote(noteId));
        });
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
