package com.ainote.app.service;

import com.ainote.app.entity.MindMap;
import com.ainote.app.repository.MindMapRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class MindMapService {

    private static final Logger log = LoggerFactory.getLogger(MindMapService.class);

    private final MindMapRepository mindMapRepository;
    private final NoteRepository noteRepository;
    private final SecurityUtils securityUtils;

    public MindMapService(MindMapRepository mindMapRepository, NoteRepository noteRepository,
                          SecurityUtils securityUtils) {
        this.mindMapRepository = mindMapRepository;
        this.noteRepository = noteRepository;
        this.securityUtils = securityUtils;
    }

    public MindMap create(String title, String data, String noteId, String source) {
        String userId = securityUtils.getCurrentUserId();
        if (noteId != null && !noteId.isBlank()
                && noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId).isEmpty()) {
            throw new NoSuchElementException("Note not found: " + noteId);
        }
        MindMap mindMap = new MindMap();
        mindMap.setUserId(userId);
        mindMap.setTitle(title);
        mindMap.setData(data);
        mindMap.setNoteId(noteId);
        mindMap.setSource(source != null ? source : "manual");
        MindMap saved = mindMapRepository.save(mindMap);
        log.info("MindMap created: id={}, title={}", saved.getId(), title);
        return saved;
    }

    public MindMap getById(String id) {
        String userId = securityUtils.getCurrentUserId();
        return mindMapRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NoSuchElementException("MindMap not found: " + id));
    }

    public List<MindMap> listByUser() {
        String userId = securityUtils.getCurrentUserId();
        return mindMapRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public MindMap update(String id, String title, String data) {
        MindMap mindMap = getById(id);
        if (title != null) mindMap.setTitle(title);
        if (data != null) mindMap.setData(data);
        return mindMapRepository.save(mindMap);
    }

    public void delete(String id) {
        String userId = securityUtils.getCurrentUserId();
        mindMapRepository.findByIdAndUserId(id, userId).ifPresent(mindMapRepository::delete);
    }

    public List<MindMap> getByNoteId(String noteId) {
        String userId = securityUtils.getCurrentUserId();
        return mindMapRepository.findByNoteIdAndUserIdOrderByUpdatedAtDesc(noteId, userId);
    }
}
