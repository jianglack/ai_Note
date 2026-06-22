package com.ainote.note.service;

import com.ainote.common.security.UserContext;
import com.ainote.note.entity.Folder;
import com.ainote.note.entity.Note;
import com.ainote.note.entity.Tag;
import com.ainote.note.model.NoteModel;
import com.ainote.note.model.NoteRequest;
import com.ainote.note.model.TagModel;
import com.ainote.note.repository.FolderRepository;
import com.ainote.note.repository.NoteRepository;
import com.ainote.note.repository.TagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class NoteService {

    private static final Logger log = LoggerFactory.getLogger(NoteService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NoteRepository noteRepository;
    private final TagRepository tagRepository;
    private final FolderRepository folderRepository;

    public NoteService(NoteRepository noteRepository, TagRepository tagRepository,
                       FolderRepository folderRepository) {
        this.noteRepository = noteRepository;
        this.tagRepository = tagRepository;
        this.folderRepository = folderRepository;
    }

    private String getCurrentUserId() {
        return String.valueOf(UserContext.getCurrentUserId());
    }

    public List<NoteModel> listAll() {
        String userId = getCurrentUserId();
        return noteRepository.findByUserIdAndDeletedAtIsNull(userId).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    public long countAll() {
        String userId = getCurrentUserId();
        return noteRepository.findByUserIdAndDeletedAtIsNull(userId).size();
    }

    public NoteModel create(NoteRequest request) {
        Note note = new Note();
        note.setId(UUID.randomUUID().toString());
        note.setTitle(request.getTitle());
        note.setContent(request.getContent());
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());

        String userId = getCurrentUserId();
        note.setUserId(userId);

        if (request.getFolderId() != null && !request.getFolderId().isEmpty()) {
            folderRepository.findById(request.getFolderId()).ifPresent(note::setFolder);
        }

        if (request.getTags() != null && !request.getTags().isEmpty()) {
            for (String tagName : request.getTags()) {
                note.getTags().add(getOrCreateTag(tagName));
            }
        }

        Note saved = noteRepository.save(note);
        log.info("Note created: {}", saved.getId());
        return toModel(saved);
    }

    @Transactional(readOnly = true)
    public Optional<NoteModel> getById(String id) {
        String userId = getCurrentUserId();
        return noteRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .map(this::toModel);
    }

    public Optional<NoteModel> update(String id, NoteRequest request) {
        String userId = getCurrentUserId();
        Optional<Note> noteOpt = noteRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId);
        if (noteOpt.isEmpty()) return Optional.empty();

        Note note = noteOpt.get();
        note.setTitle(request.getTitle());
        note.setContent(request.getContent());
        note.setUpdatedAt(LocalDateTime.now());

        if (request.getFolderId() != null && !request.getFolderId().isEmpty()) {
            folderRepository.findById(request.getFolderId()).ifPresent(note::setFolder);
        } else {
            note.setFolder(null);
        }

        if (request.getTags() != null) {
            note.getTags().clear();
            for (String tagName : request.getTags()) {
                note.getTags().add(getOrCreateTag(tagName));
            }
        }

        Note saved = noteRepository.save(note);
        return Optional.of(toModel(saved));
    }

    public void delete(String id) {
        noteRepository.findById(id).ifPresent(note -> {
            note.setDeletedAt(LocalDateTime.now());
            noteRepository.save(note);
        });
    }

    @Transactional(readOnly = true)
    public List<NoteModel> search(String query) {
        String userId = getCurrentUserId();
        return noteRepository.searchByUserIdAndQuery(userId, query).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NoteModel> getByFolderId(String folderId) {
        String userId = getCurrentUserId();
        if (folderId == null) {
            return noteRepository.findByUserIdAndDeletedAtIsNull(userId).stream()
                    .filter(note -> note.getFolder() == null)
                    .map(this::toModel)
                    .collect(Collectors.toList());
        }
        return noteRepository.findByFolderIdAndDeletedAtIsNull(folderId).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NoteModel> listDeleted() {
        String userId = getCurrentUserId();
        return noteRepository.findByUserIdAndDeletedAtIsNotNull(userId).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    public void restore(String id) {
        noteRepository.findById(id).ifPresent(note -> {
            note.setDeletedAt(null);
            noteRepository.save(note);
        });
    }

    public void permanentDelete(String id) {
        noteRepository.deleteById(id);
    }

    public Optional<NoteModel> moveToFolder(String noteId, String folderId) {
        String userId = getCurrentUserId();
        Optional<Note> noteOpt = noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId);
        if (noteOpt.isEmpty()) return Optional.empty();

        Note note = noteOpt.get();
        if (folderId == null || folderId.isEmpty()) {
            note.setFolder(null);
        } else {
            Optional<Folder> folderOpt = folderRepository.findById(folderId);
            if (folderOpt.isEmpty()) return Optional.empty();
            note.setFolder(folderOpt.get());
        }
        note.setUpdatedAt(LocalDateTime.now());
        Note saved = noteRepository.save(note);
        return Optional.of(toModel(saved));
    }

    public NoteModel copy(String noteId) {
        String userId = getCurrentUserId();
        Optional<Note> noteOpt = noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId);
        if (noteOpt.isEmpty()) return null;

        Note original = noteOpt.get();
        Note copy = new Note();
        copy.setId(UUID.randomUUID().toString());
        copy.setTitle(original.getTitle() + " (副本)");
        copy.setContent(original.getContent());
        copy.setFolder(original.getFolder());
        copy.setUserId(original.getUserId());
        copy.setCreatedAt(LocalDateTime.now());
        copy.setUpdatedAt(LocalDateTime.now());

        if (original.getTags() != null) {
            copy.getTags().addAll(original.getTags());
        }

        Note saved = noteRepository.save(copy);
        return toModel(saved);
    }

    public NoteModel merge(List<String> noteIds, String newTitle) {
        String userId = getCurrentUserId();
        StringBuilder mergedContent = new StringBuilder();
        Set<Tag> mergedTags = new HashSet<>();
        Folder folder = null;

        for (String noteId : noteIds) {
            Optional<Note> noteOpt = noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId);
            if (noteOpt.isPresent()) {
                Note note = noteOpt.get();
                mergedContent.append("## ").append(note.getTitle()).append("\n\n");
                if (note.getContent() != null && !note.getContent().isEmpty()) {
                    mergedContent.append(note.getContent()).append("\n\n");
                }
                if (note.getTags() != null) {
                    mergedTags.addAll(note.getTags());
                }
                if (folder == null && note.getFolder() != null) {
                    folder = note.getFolder();
                }
            }
        }

        Note merged = new Note();
        merged.setId(UUID.randomUUID().toString());
        merged.setTitle(newTitle != null ? newTitle : "合并的笔记");
        merged.setContent(mergedContent.toString().trim());
        merged.setFolder(folder);
        merged.setUserId(userId);
        merged.setCreatedAt(LocalDateTime.now());
        merged.setUpdatedAt(LocalDateTime.now());
        merged.getTags().addAll(mergedTags);

        Note saved = noteRepository.save(merged);
        return toModel(saved);
    }

    public int emptyTrash() {
        String userId = getCurrentUserId();
        List<Note> deletedNotes = noteRepository.findByUserIdAndDeletedAtIsNotNull(userId);
        int count = deletedNotes.size();
        for (Note note : deletedNotes) {
            noteRepository.delete(note);
        }
        return count;
    }

    /**
     * For search-service reindex: returns all non-deleted notes with pagination.
     */
    @Transactional(readOnly = true)
    public Page<NoteModel> findAll(Pageable pageable) {
        return noteRepository.findByDeletedAtIsNull(pageable)
                .map(this::toModel);
    }

    private NoteModel toModel(Note entity) {
        NoteModel model = new NoteModel();
        model.setId(entity.getId());
        model.setTitle(entity.getTitle());
        model.setContent(entity.getContent());
        model.setFolderId(entity.getFolder() != null ? entity.getFolder().getId() : null);
        model.setCreatedAt(entity.getCreatedAt().format(FORMATTER));
        model.setUpdatedAt(entity.getUpdatedAt().format(FORMATTER));
        model.setDeletedAt(entity.getDeletedAt() != null ? entity.getDeletedAt().format(FORMATTER) : null);
        model.setPinned(entity.isPinned());
        model.setStarred(entity.isStarred());
        model.setArchived(entity.isArchived());

        if (entity.getTags() != null && !entity.getTags().isEmpty()) {
            List<TagModel> tags = entity.getTags().stream()
                    .map(tag -> {
                        TagModel t = new TagModel();
                        t.setId(tag.getId());
                        t.setName(tag.getName());
                        return t;
                    })
                    .collect(Collectors.toList());
            model.setTags(tags);
        }

        return model;
    }

    private Tag getOrCreateTag(String tagName) {
        return tagRepository.findByName(tagName)
                .orElseGet(() -> {
                    Tag tag = new Tag();
                    tag.setId(UUID.randomUUID().toString());
                    tag.setName(tagName);
                    return tagRepository.save(tag);
                });
    }
}
