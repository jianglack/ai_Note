package com.ainote.app.repository;

import com.ainote.app.entity.NoteDatabase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NoteDatabaseRepository extends JpaRepository<NoteDatabase, String> {
    List<NoteDatabase> findByNoteId(String noteId);
    List<NoteDatabase> findByNoteIdAndUserId(String noteId, String userId);
    List<NoteDatabase> findByUserId(String userId);
    Optional<NoteDatabase> findByIdAndUserId(String id, String userId);
}
