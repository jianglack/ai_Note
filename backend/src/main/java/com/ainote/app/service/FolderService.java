package com.ainote.app.service;

import com.ainote.app.entity.Folder;
import com.ainote.app.entity.Note;
import com.ainote.app.entity.User;
import com.ainote.app.repository.FolderRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class FolderService {

    private final FolderRepository folderRepository;
    private final NoteRepository noteRepository;
    private final SecurityUtils securityUtils;
    private final KnowledgeGraphService knowledgeGraphService;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public FolderService(FolderRepository folderRepository, NoteRepository noteRepository,
                         SecurityUtils securityUtils, KnowledgeGraphService knowledgeGraphService) {
        this.folderRepository = folderRepository;
        this.noteRepository = noteRepository;
        this.securityUtils = securityUtils;
        this.knowledgeGraphService = knowledgeGraphService;
    }

    public List<com.ainote.app.model.Folder> listAll() {
        String userId = securityUtils.getCurrentUserId();
        return folderRepository.findByUserId(userId).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    public Optional<com.ainote.app.model.Folder> getById(String id) {
        String userId = securityUtils.getCurrentUserId();
        return folderRepository.findByIdAndUserId(id, userId).map(this::toModel);
    }

    public com.ainote.app.model.Folder create(String name, String parentId) {
        User user = securityUtils.getCurrentUser();
        if (parentId != null && !parentId.isBlank()
                && folderRepository.findByIdAndUserId(parentId, user.getId()).isEmpty()) {
            throw new IllegalArgumentException("Parent folder not found");
        }

        Folder folder = new Folder();
        folder.setId(UUID.randomUUID().toString());
        folder.setName(name);
        folder.setParentId(parentId);
        folder.setUser(user);
        folder.setCreatedAt(LocalDateTime.now());
        folder.setUpdatedAt(LocalDateTime.now());

        Folder saved = folderRepository.save(folder);
        afterCommit(() -> knowledgeGraphService.syncFolder(saved.getId()));
        return toModel(saved);
    }

    public com.ainote.app.model.Folder update(String id, String name, String parentId) {
        String userId = securityUtils.getCurrentUserId();
        Optional<Folder> folderOpt = folderRepository.findByIdAndUserId(id, userId);
        if (folderOpt.isEmpty()) return null;
        if (parentId != null && !parentId.isBlank()
                && folderRepository.findByIdAndUserId(parentId, userId).isEmpty()) {
            return null;
        }

        Folder folder = folderOpt.get();
        folder.setName(name);
        folder.setParentId(parentId);
        folder.setUpdatedAt(LocalDateTime.now());

        Folder saved = folderRepository.save(folder);
        afterCommit(() -> knowledgeGraphService.syncFolder(saved.getId()));
        return toModel(saved);
    }

    public void delete(String id) {
        String userId = securityUtils.getCurrentUserId();
        if (folderRepository.findByIdAndUserId(id, userId).isEmpty()) {
            return;
        }
        // 1. 把该文件夹下的所有笔记（包括回收站的）移出
        List<Note> notesInFolder = noteRepository.findByFolderIdAndUserId(id, userId);
        List<String> noteIds = notesInFolder.stream().map(Note::getId).collect(Collectors.toList());
        for (Note note : notesInFolder) {
            note.setFolder(null);
            noteRepository.save(note);
        }

        // 强制刷新，确保更新在删除前提交
        noteRepository.flush();

        // 2. 把子文件夹移到根目录
        List<Folder> children = folderRepository.findByParentIdAndUserId(id, userId);
        for (Folder child : children) {
            child.setParentId(null);
            folderRepository.save(child);
        }

        folderRepository.flush();

        // 3. 删除文件夹
        folderRepository.deleteById(id);
        afterCommit(() -> {
            knowledgeGraphService.deleteFolder(id);
            noteIds.forEach(knowledgeGraphService::syncNote);
        });
    }

    public List<com.ainote.app.model.Folder> getRootFolders() {
        String userId = securityUtils.getCurrentUserId();
        return folderRepository.findByUserIdAndParentIdIsNull(userId).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    public List<com.ainote.app.model.Folder> getChildren(String parentId) {
        String userId = securityUtils.getCurrentUserId();
        return folderRepository.findByParentIdAndUserId(parentId, userId).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    private com.ainote.app.model.Folder toModel(Folder entity) {
        com.ainote.app.model.Folder model = new com.ainote.app.model.Folder();
        model.setId(entity.getId());
        model.setName(entity.getName());
        model.setParentId(entity.getParentId());
        model.setCreatedAt(entity.getCreatedAt().format(FORMATTER));
        model.setUpdatedAt(entity.getUpdatedAt().format(FORMATTER));
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
