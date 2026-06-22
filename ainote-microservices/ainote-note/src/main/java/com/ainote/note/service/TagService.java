package com.ainote.note.service;

import com.ainote.note.entity.Note;
import com.ainote.note.entity.Tag;
import com.ainote.note.model.TagModel;
import com.ainote.note.repository.NoteRepository;
import com.ainote.note.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TagService {

    private final TagRepository tagRepository;
    private final NoteRepository noteRepository;

    public TagService(TagRepository tagRepository, NoteRepository noteRepository) {
        this.tagRepository = tagRepository;
        this.noteRepository = noteRepository;
    }

    public TagModel create(String name) {
        Tag tag = new Tag();
        tag.setId(UUID.randomUUID().toString());
        tag.setName(name);
        Tag saved = tagRepository.save(tag);

        TagModel model = new TagModel();
        model.setId(saved.getId());
        model.setName(saved.getName());
        return model;
    }

    @Transactional(readOnly = true)
    public List<TagModel> listAll() {
        return tagRepository.findAll().stream()
                .map(tag -> {
                    TagModel model = new TagModel();
                    model.setId(tag.getId());
                    model.setName(tag.getName());
                    return model;
                })
                .collect(Collectors.toList());
    }

    public void delete(String id) {
        tagRepository.deleteById(id);
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
        });
    }
}
