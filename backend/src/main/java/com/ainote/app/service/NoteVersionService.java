package com.ainote.app.service;

import com.ainote.app.repository.NoteVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class NoteVersionService {
    
    private final NoteVersionRepository noteVersionRepository;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public NoteVersionService(NoteVersionRepository noteVersionRepository) {
        this.noteVersionRepository = noteVersionRepository;
    }
    
    public List<Map<String, Object>> getVersions(String noteId) {
        return noteVersionRepository.findByNoteIdOrderByCreatedAtDesc(noteId).stream()
                .map(version -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", version.getId());
                    map.put("noteId", version.getNote().getId());
                    map.put("content", version.getContent());
                    map.put("createdAt", version.getCreatedAt().format(FORMATTER));
                    return map;
                })
                .collect(Collectors.toList());
    }
}
