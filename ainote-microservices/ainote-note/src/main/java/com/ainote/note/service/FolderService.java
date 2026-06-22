package com.ainote.note.service;

import com.ainote.common.security.UserContext;
import com.ainote.note.entity.Folder;
import com.ainote.note.entity.Note;
import com.ainote.note.model.FolderModel;
import com.ainote.note.repository.FolderRepository;
import com.ainote.note.repository.NoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public FolderService(FolderRepository folderRepository, NoteRepository noteRepository) {
        this.folderRepository = folderRepository;
        this.noteRepository = noteRepository;
    }

    private String getCurrentUserId() {
        return String.valueOf(UserContext.getCurrentUserId());
    }

    public List<FolderModel> listAll() {
        String userId = getCurrentUserId();
        return folderRepository.findByUserId(userId).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    public Optional<FolderModel> getById(String id) {
        return folderRepository.findById(id).map(this::toModel);
    }

    public FolderModel create(String name, String parentId) {
        String userId = getCurrentUserId();

        Folder folder = new Folder();
        folder.setId(UUID.randomUUID().toString());
        folder.setName(name);
        folder.setParentId(parentId);
        folder.setUserId(userId);
        folder.setCreatedAt(LocalDateTime.now());
        folder.setUpdatedAt(LocalDateTime.now());

        Folder saved = folderRepository.save(folder);
        return toModel(saved);
    }

    public FolderModel update(String id, String name, String parentId) {
        Optional<Folder> folderOpt = folderRepository.findById(id);
        if (folderOpt.isEmpty()) return null;

        Folder folder = folderOpt.get();
        folder.setName(name);
        folder.setParentId(parentId);
        folder.setUpdatedAt(LocalDateTime.now());

        Folder saved = folderRepository.save(folder);
        return toModel(saved);
    }

    public void delete(String id) {
        // Move all notes out of this folder
        List<Note> notesInFolder = noteRepository.findByFolderId(id);
        for (Note note : notesInFolder) {
            note.setFolder(null);
            noteRepository.save(note);
        }
        noteRepository.flush();

        // Move child folders to root
        List<Folder> children = folderRepository.findByParentId(id);
        for (Folder child : children) {
            child.setParentId(null);
            folderRepository.save(child);
        }
        folderRepository.flush();

        // Delete the folder
        folderRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<FolderModel> getRootFolders() {
        String userId = getCurrentUserId();
        return folderRepository.findByUserIdAndParentIdIsNull(userId).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FolderModel> getChildren(String parentId) {
        return folderRepository.findByParentId(parentId).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    private FolderModel toModel(Folder entity) {
        FolderModel model = new FolderModel();
        model.setId(entity.getId());
        model.setName(entity.getName());
        model.setParentId(entity.getParentId());
        model.setCreatedAt(entity.getCreatedAt().format(FORMATTER));
        model.setUpdatedAt(entity.getUpdatedAt().format(FORMATTER));
        return model;
    }
}
