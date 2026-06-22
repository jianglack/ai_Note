package com.ainote.app.repository;

import com.ainote.app.entity.NoteMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NoteMediaRepository extends JpaRepository<NoteMedia, String> {
    List<NoteMedia> findByNoteId(String noteId);
    List<NoteMedia> findByNoteIdAndUserId(String noteId, String userId);
    List<NoteMedia> findByNoteIdAndMediaType(String noteId, String mediaType);
    List<NoteMedia> findByUserId(String userId);
    Optional<NoteMedia> findByIdAndUserId(String id, String userId);
    void deleteByNoteId(String noteId);
}
