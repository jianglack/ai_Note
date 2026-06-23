package com.ainote.app.service;

import com.ainote.app.entity.Folder;
import com.ainote.app.entity.Note;
import com.ainote.app.entity.NoteVersion;
import com.ainote.app.entity.Tag;
import com.ainote.app.entity.User;
import com.ainote.app.model.NoteRequest;
import com.ainote.app.repository.FolderRepository;
import com.ainote.app.repository.NoteVersionRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.TagRepository;
import com.ainote.app.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class NoteService {

    private static final Logger log = LoggerFactory.getLogger(NoteService.class);
    private static final int DEFAULT_LIST_LIMIT = 100;

    private final NoteRepository noteRepository;
    private final NoteVersionRepository noteVersionRepository;
    private final TagRepository tagRepository;
    private final FolderRepository folderRepository;
    private final SecurityUtils securityUtils;
    private final CacheService cacheService;
    private final LangChain4jRagService langChain4jRagService;
    private final KnowledgeGraphService knowledgeGraphService;
    private final ContentAnalysisService contentAnalysisService;

    @Value("${app.versions.max:20}")
    private int maxVersions;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public NoteService(NoteRepository noteRepository, NoteVersionRepository noteVersionRepository,
                       TagRepository tagRepository,
                       FolderRepository folderRepository, SecurityUtils securityUtils,
                       CacheService cacheService,
                       LangChain4jRagService langChain4jRagService,
                       KnowledgeGraphService knowledgeGraphService,
                       ContentAnalysisService contentAnalysisService) {
        this.noteRepository = noteRepository;
        this.noteVersionRepository = noteVersionRepository;
        this.tagRepository = tagRepository;
        this.folderRepository = folderRepository;
        this.securityUtils = securityUtils;
        this.cacheService = cacheService;
        this.langChain4jRagService = langChain4jRagService;
        this.knowledgeGraphService = knowledgeGraphService;
        this.contentAnalysisService = contentAnalysisService;
    }

    public List<com.ainote.app.model.Note> listAll() {
        String userId = securityUtils.getCurrentUserId();
        return noteRepository.findByUserIdWithTagsAndFolder(userId).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    public long countAll() {
        String userId = securityUtils.getCurrentUserId();
        return noteRepository.countByUserIdAndDeletedAtIsNull(userId);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Page<com.ainote.app.model.Note> listAllPaged(Pageable pageable) {
        String userId = securityUtils.getCurrentUserId();
        Page<String> idPage = noteRepository.findNoteIdsByUserId(userId, pageable);
        List<String> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, idPage.getTotalElements());
        }

        Map<String, Note> notesById = noteRepository.findByIdsWithTagsAndFolder(ids, userId).stream()
                .collect(Collectors.toMap(Note::getId, note -> note));
        List<com.ainote.app.model.Note> notes = ids.stream()
                .map(notesById::get)
                .filter(Objects::nonNull)
                .map(this::toModel)
                .collect(Collectors.toList());

        return new PageImpl<>(notes, pageable, idPage.getTotalElements());
    }

    public com.ainote.app.model.Note create(NoteRequest request) {
        Note note = new Note();
        note.setId(UUID.randomUUID().toString());
        note.setTitle(request.getTitle());
        note.setContent(request.getContent());
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());

        User user = securityUtils.getCurrentUser();
        note.setUser(user);

        if (request.getFolderId() != null && !request.getFolderId().isEmpty()) {
            folderRepository.findByIdAndUserId(request.getFolderId(), user.getId()).ifPresent(note::setFolder);
        }

        if (request.getTags() != null && !request.getTags().isEmpty()) {
            for (String tagName : request.getTags()) {
                note.getTags().add(getOrCreateTag(tagName, user.getId()));
            }
        }

        Note saved = noteRepository.save(note);
        log.info("Note saved, calling generateEmbeddingAsync for note: {}", saved.getId());

        String noteId = saved.getId();
        String userId = user.getId();
        String content = saved.getContent() != null ? saved.getContent() : "";
        List<String> tagNames = saved.getTags().stream()
                .map(com.ainote.app.entity.Tag::getName)
                .collect(Collectors.toList());
        String folderId = saved.getFolder() != null ? saved.getFolder().getId() : null;
        
        log.info("=== Preparing afterCommit callback for user: {}, noteId: {}, tags: {}", userId, noteId, tagNames);
        
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("=== afterCommit triggered for noteId: {}", noteId);

                // 生成 embedding
                langChain4jRagService.generateEmbeddingAsync(noteId);
                knowledgeGraphService.syncNote(noteId);
                // 自动提取概念
                contentAnalysisService.extractConceptsAsync(noteId, userId,
                        request.getTitle(), content);
            }
        });

        log.info("generateEmbeddingAsync registered for after commit");
        return toModel(saved);
    }

    public Optional<com.ainote.app.model.Note> getById(String id) {
        String userId = securityUtils.getCurrentUserId();
        return noteRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .map(this::toModel);
    }

    public Optional<com.ainote.app.model.Note> update(String id, NoteRequest request) {
        String userId = securityUtils.getCurrentUserId();
        Optional<Note> noteOpt = noteRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId);
        if (noteOpt.isEmpty()) return Optional.empty();

        Note note = noteOpt.get();
        saveVersionSnapshot(note);
        note.setTitle(request.getTitle());
        note.setContent(request.getContent());
        note.setUpdatedAt(LocalDateTime.now());
        if (request.getPinned() != null) {
            note.setPinned(request.getPinned());
        }
        if (request.getStarred() != null) {
            note.setStarred(request.getStarred());
        }
        if (request.getArchived() != null) {
            note.setArchived(request.getArchived());
        }

        if (request.getFolderId() != null && !request.getFolderId().isEmpty()) {
            Optional<Folder> folderOpt = folderRepository.findByIdAndUserId(request.getFolderId(), userId);
            if (folderOpt.isEmpty()) return Optional.empty();
            note.setFolder(folderOpt.get());
        } else {
            note.setFolder(null);
        }

        if (request.getTags() != null) {
            note.getTags().clear();
            for (String tagName : request.getTags()) {
                note.getTags().add(getOrCreateTag(tagName, userId));
            }
        }

        Note saved = noteRepository.save(note);
        cacheService.invalidateNoteCache(id);
        // 触发异步嵌入生成
        String currentUserId = userId;
        afterCommit(() -> {
            langChain4jRagService.generateEmbeddingAsync(saved.getId());
            knowledgeGraphService.syncNote(saved.getId());
            contentAnalysisService.extractConceptsAsync(saved.getId(), currentUserId,
                    saved.getTitle(), saved.getContent());
        });
        return Optional.of(toModel(saved));
    }

    private void saveVersionSnapshot(Note note) {
        NoteVersion snapshot = new NoteVersion(
                UUID.randomUUID().toString(),
                note,
                note.getContent(),
                LocalDateTime.now()
        );
        noteVersionRepository.save(snapshot);
        pruneOldVersions(note.getId());
    }

    private void pruneOldVersions(String noteId) {
        long count = noteVersionRepository.countByNoteId(noteId);
        if (count <= maxVersions) {
            return;
        }
        int removeCount = (int) (count - maxVersions);
        List<String> versionIds = noteVersionRepository.findOldestIdsByNoteId(
                noteId, PageRequest.of(0, removeCount));
        noteVersionRepository.deleteAllByIdInBatch(versionIds);
    }

    public void delete(String id) {
        // 软删除：设置 deletedAt，并限制为当前用户自己的笔记
        String userId = securityUtils.getCurrentUserId();
        noteRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId).ifPresent(note -> {
            note.setDeletedAt(LocalDateTime.now());
            noteRepository.save(note);
            afterCommit(() -> knowledgeGraphService.syncNote(id));
        });
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<com.ainote.app.model.Note> search(String query) {
        String userId = securityUtils.getCurrentUserId();
        return noteRepository.searchByUserIdAndQuery(userId, query).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    /**
     * 混合搜索（关键词 + 语义）
     * LangChain4j 已内置 Query Rewriting，直接使用语义搜索即可
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<com.ainote.app.model.Note> hybridSearch(String query) {
        if (query == null || query.isBlank()) {
            return listAllPaged(PageRequest.of(0, DEFAULT_LIST_LIMIT)).getContent();
        }

        // 使用 LangChain4j 的语义搜索（已包含 Query Rewriting）
        String userId = securityUtils.getCurrentUserId();
        List<com.ainote.app.model.Note> vectorResults = langChain4jRagService.searchSimilar(query, 10);
        List<String> seedNoteIds = vectorResults.stream()
                .map(com.ainote.app.model.Note::getId)
                .collect(Collectors.toList());

        List<Note> graphResults = knowledgeGraphService.searchRelatedNotes(userId, query, seedNoteIds, 10);

        Map<String, com.ainote.app.model.Note> merged = new LinkedHashMap<>();
        for (com.ainote.app.model.Note note : vectorResults) {
            merged.put(note.getId(), note);
        }
        for (Note note : graphResults) {
            merged.putIfAbsent(note.getId(), toModel(note));
        }
        return merged.values().stream()
                .limit(10)
                .collect(Collectors.toList());
    }

    public List<com.ainote.app.model.Note> getByFolderId(String folderId) {
        String userId = securityUtils.getCurrentUserId();
        if (folderId == null) {
            return noteRepository.findByUserIdAndDeletedAtIsNull(userId).stream()
                    .filter(note -> note.getFolder() == null)
                    .map(this::toModel)
                    .collect(Collectors.toList());
        }
        return noteRepository.findByFolderIdAndUserIdAndDeletedAtIsNull(folderId, userId).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    public List<com.ainote.app.model.Note> listDeleted() {
        String userId = securityUtils.getCurrentUserId();
        return noteRepository.findByUserIdAndDeletedAtIsNotNull(userId).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    public void restore(String id) {
        String userId = securityUtils.getCurrentUserId();
        noteRepository.findByIdAndUserId(id, userId).ifPresent(note -> {
            note.setDeletedAt(null);
            noteRepository.save(note);
            afterCommit(() -> knowledgeGraphService.syncNote(id));
        });
    }

    public void permanentDelete(String id) {
        String userId = securityUtils.getCurrentUserId();
        noteRepository.findByIdAndUserId(id, userId).ifPresent(note -> {
            noteRepository.delete(note);
            afterCommit(() -> knowledgeGraphService.deleteNote(id));
        });
    }

    /**
     * 移动笔记到文件夹
     */
    public Optional<com.ainote.app.model.Note> moveToFolder(String noteId, String folderId) {
        String userId = securityUtils.getCurrentUserId();
        Optional<Note> noteOpt = noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId);
        if (noteOpt.isEmpty()) return Optional.empty();

        Note note = noteOpt.get();
        if (folderId == null || folderId.isEmpty()) {
            note.setFolder(null);
        } else {
            Optional<Folder> folderOpt = folderRepository.findByIdAndUserId(folderId, userId);
            if (folderOpt.isEmpty()) return Optional.empty();
            note.setFolder(folderOpt.get());
        }
        note.setUpdatedAt(LocalDateTime.now());
        Note saved = noteRepository.save(note);
        afterCommit(() -> knowledgeGraphService.syncNote(saved.getId()));
        return Optional.of(toModel(saved));
    }

    /**
     * 复制笔记
     */
    public com.ainote.app.model.Note copy(String noteId) {
        String userId = securityUtils.getCurrentUserId();
        Optional<Note> noteOpt = noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId);
        if (noteOpt.isEmpty()) return null;

        Note original = noteOpt.get();
        Note copy = new Note();
        copy.setId(UUID.randomUUID().toString());
        copy.setTitle(original.getTitle() + " (副本)");
        copy.setContent(original.getContent());
        copy.setFolder(original.getFolder());
        copy.setUser(original.getUser());
        copy.setCreatedAt(LocalDateTime.now());
        copy.setUpdatedAt(LocalDateTime.now());

        // 复制标签
        if (original.getTags() != null) {
            copy.getTags().addAll(original.getTags());
        }

        Note saved = noteRepository.save(copy);
        // 异步生成嵌入
        String newNoteId = saved.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                langChain4jRagService.generateEmbeddingAsync(newNoteId);
                knowledgeGraphService.syncNote(newNoteId);
            }
        });
        return toModel(saved);
    }

    /**
     * 合并多条笔记
     */
    public com.ainote.app.model.Note merge(List<String> noteIds, String newTitle) {
        String userId = securityUtils.getCurrentUserId();
        StringBuilder mergedContent = new StringBuilder();
        java.util.Set<Tag> mergedTags = new java.util.HashSet<>();
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
        merged.setUser(securityUtils.getCurrentUser());
        merged.setCreatedAt(LocalDateTime.now());
        merged.setUpdatedAt(LocalDateTime.now());
        merged.getTags().addAll(mergedTags);

        Note saved = noteRepository.save(merged);
        String newNoteId = saved.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                langChain4jRagService.generateEmbeddingAsync(newNoteId);
                knowledgeGraphService.syncNote(newNoteId);
            }
        });
        return toModel(saved);
    }

    /**
     * 清空回收站
     */
    public int emptyTrash() {
        String userId = securityUtils.getCurrentUserId();
        List<Note> deletedNotes = noteRepository.findByUserIdAndDeletedAtIsNotNull(userId);
        int count = deletedNotes.size();
        List<String> deletedNoteIds = deletedNotes.stream()
                .map(Note::getId)
                .collect(Collectors.toList());
        for (Note note : deletedNotes) {
            noteRepository.delete(note);
        }
        afterCommit(() -> deletedNoteIds.forEach(knowledgeGraphService::deleteNote));
        return count;
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

    private com.ainote.app.model.Note toModel(Note entity) {
        com.ainote.app.model.Note model = new com.ainote.app.model.Note();
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
            List<com.ainote.app.model.Tag> tags = entity.getTags().stream()
                    .map(tag -> {
                        com.ainote.app.model.Tag t = new com.ainote.app.model.Tag();
                        t.setId(tag.getId());
                        t.setName(tag.getName());
                        return t;
                    })
                    .collect(Collectors.toList());
            model.setTags(tags);
        }

        return model;
    }

    private Tag getOrCreateTag(String tagName, String userId) {
        return tagRepository.findByNameAndUserId(tagName, userId)
                .orElseGet(() -> {
                    Tag tag = new Tag();
                    tag.setId(UUID.randomUUID().toString());
                    tag.setName(tagName);
                    tag.setUserId(userId);
                    return tagRepository.save(tag);
                });
    }
}
