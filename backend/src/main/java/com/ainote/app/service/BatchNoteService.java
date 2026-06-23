package com.ainote.app.service;

import com.ainote.app.entity.Folder;
import com.ainote.app.entity.Note;
import com.ainote.app.entity.Tag;
import com.ainote.app.model.BatchOperationResult;
import com.ainote.app.repository.FolderRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.TagRepository;
import com.ainote.app.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class BatchNoteService {

    private static final Logger log = LoggerFactory.getLogger(BatchNoteService.class);

    private final NoteRepository noteRepository;
    private final TagRepository tagRepository;
    private final FolderRepository folderRepository;
    private final SecurityUtils securityUtils;
    private final KnowledgeGraphService knowledgeGraphService;

    public BatchNoteService(NoteRepository noteRepository,
                            TagRepository tagRepository,
                            FolderRepository folderRepository,
                            SecurityUtils securityUtils,
                            KnowledgeGraphService knowledgeGraphService) {
        this.noteRepository = noteRepository;
        this.tagRepository = tagRepository;
        this.folderRepository = folderRepository;
        this.securityUtils = securityUtils;
        this.knowledgeGraphService = knowledgeGraphService;
    }

    public BatchOperationResult batchDelete(List<String> noteIds) {
        String userId = securityUtils.getCurrentUserId();
        BatchOperationResult result = new BatchOperationResult();
        for (String noteId : noteIds) {
            try {
                Optional<Note> noteOpt = noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId);
                if (noteOpt.isEmpty()) {
                    result.addFailure(noteId, noteId, "笔记不存在或无权操作");
                    continue;
                }
                Note note = noteOpt.get();
                note.setDeletedAt(LocalDateTime.now());
                noteRepository.save(note);
                knowledgeGraphService.syncNote(noteId);
                result.addSuccess(noteId, note.getTitle());
            } catch (Exception e) {
                log.error("Batch delete failed for note {}: {}", noteId, e.getMessage());
                result.addFailure(noteId, noteId, e.getMessage());
            }
        }
        return result;
    }

    public BatchOperationResult batchPermanentDelete(List<String> noteIds) {
        String userId = securityUtils.getCurrentUserId();
        BatchOperationResult result = new BatchOperationResult();
        for (String noteId : noteIds) {
            try {
                Optional<Note> noteOpt = noteRepository.findByIdAndUserId(noteId, userId);
                if (noteOpt.isEmpty()) {
                    result.addFailure(noteId, noteId, "笔记不存在或无权操作");
                    continue;
                }
                String title = noteOpt.get().getTitle();
                noteRepository.deleteById(noteId);
                knowledgeGraphService.deleteNote(noteId);
                result.addSuccess(noteId, title);
            } catch (Exception e) {
                log.error("Batch permanent delete failed for note {}: {}", noteId, e.getMessage());
                result.addFailure(noteId, noteId, e.getMessage());
            }
        }
        return result;
    }

    public BatchOperationResult batchRestore(List<String> noteIds) {
        String userId = securityUtils.getCurrentUserId();
        BatchOperationResult result = new BatchOperationResult();
        for (String noteId : noteIds) {
            try {
                Optional<Note> noteOpt = noteRepository.findByIdAndUserId(noteId, userId);
                if (noteOpt.isEmpty()) {
                    result.addFailure(noteId, noteId, "笔记不存在或无权操作");
                    continue;
                }
                Note note = noteOpt.get();
                if (note.getDeletedAt() == null) {
                    result.addFailure(noteId, note.getTitle(), "笔记不在回收站中");
                    continue;
                }
                note.setDeletedAt(null);
                noteRepository.save(note);
                knowledgeGraphService.syncNote(noteId);
                result.addSuccess(noteId, note.getTitle());
            } catch (Exception e) {
                log.error("Batch restore failed for note {}: {}", noteId, e.getMessage());
                result.addFailure(noteId, noteId, e.getMessage());
            }
        }
        return result;
    }

    public BatchOperationResult batchMove(List<String> noteIds, String folderId) {
        String userId = securityUtils.getCurrentUserId();
        BatchOperationResult result = new BatchOperationResult();

        Folder targetFolder = null;
        if (folderId != null && !folderId.isEmpty()) {
            Optional<Folder> folderOpt = folderRepository.findByIdAndUserId(folderId, userId);
            if (folderOpt.isEmpty()) {
                for (String noteId : noteIds) {
                    result.addFailure(noteId, noteId, "目标文件夹不存在");
                }
                return result;
            }
            targetFolder = folderOpt.get();
        }

        for (String noteId : noteIds) {
            try {
                Optional<Note> noteOpt = noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId);
                if (noteOpt.isEmpty()) {
                    result.addFailure(noteId, noteId, "笔记不存在或无权操作");
                    continue;
                }
                Note note = noteOpt.get();
                note.setFolder(targetFolder);
                note.setUpdatedAt(LocalDateTime.now());
                noteRepository.save(note);
                knowledgeGraphService.syncNote(noteId);
                result.addSuccess(noteId, note.getTitle());
            } catch (Exception e) {
                log.error("Batch move failed for note {}: {}", noteId, e.getMessage());
                result.addFailure(noteId, noteId, e.getMessage());
            }
        }
        return result;
    }

    public BatchOperationResult batchAddTag(List<String> noteIds, String tagName) {
        String userId = securityUtils.getCurrentUserId();
        BatchOperationResult result = new BatchOperationResult();
        Tag tag = getOrCreateTag(tagName, userId);

        for (String noteId : noteIds) {
            try {
                Optional<Note> noteOpt = noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId);
                if (noteOpt.isEmpty()) {
                    result.addFailure(noteId, noteId, "笔记不存在或无权操作");
                    continue;
                }
                Note note = noteOpt.get();
                note.getTags().add(tag);
                note.setUpdatedAt(LocalDateTime.now());
                noteRepository.save(note);
                result.addSuccess(noteId, note.getTitle());
            } catch (Exception e) {
                log.error("Batch add tag failed for note {}: {}", noteId, e.getMessage());
                result.addFailure(noteId, noteId, e.getMessage());
            }
        }
        return result;
    }

    public BatchOperationResult batchRemoveTag(List<String> noteIds, String tagName) {
        String userId = securityUtils.getCurrentUserId();
        BatchOperationResult result = new BatchOperationResult();

        Optional<Tag> tagOpt = tagRepository.findByNameAndUserId(tagName, userId);
        if (tagOpt.isEmpty()) {
            for (String noteId : noteIds) {
                result.addFailure(noteId, noteId, "标签不存在: " + tagName);
            }
            return result;
        }
        Tag tag = tagOpt.get();

        for (String noteId : noteIds) {
            try {
                Optional<Note> noteOpt = noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId);
                if (noteOpt.isEmpty()) {
                    result.addFailure(noteId, noteId, "笔记不存在或无权操作");
                    continue;
                }
                Note note = noteOpt.get();
                note.getTags().remove(tag);
                note.setUpdatedAt(LocalDateTime.now());
                noteRepository.save(note);
                result.addSuccess(noteId, note.getTitle());
            } catch (Exception e) {
                log.error("Batch remove tag failed for note {}: {}", noteId, e.getMessage());
                result.addFailure(noteId, noteId, e.getMessage());
            }
        }
        return result;
    }

    public BatchOperationResult batchArchive(List<String> noteIds, boolean archive) {
        String userId = securityUtils.getCurrentUserId();
        BatchOperationResult result = new BatchOperationResult();
        for (String noteId : noteIds) {
            try {
                Optional<Note> noteOpt = noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId);
                if (noteOpt.isEmpty()) {
                    result.addFailure(noteId, noteId, "笔记不存在或无权操作");
                    continue;
                }
                Note note = noteOpt.get();
                note.setArchived(archive);
                note.setUpdatedAt(LocalDateTime.now());
                noteRepository.save(note);
                result.addSuccess(noteId, note.getTitle());
            } catch (Exception e) {
                log.error("Batch archive failed for note {}: {}", noteId, e.getMessage());
                result.addFailure(noteId, noteId, e.getMessage());
            }
        }
        return result;
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
