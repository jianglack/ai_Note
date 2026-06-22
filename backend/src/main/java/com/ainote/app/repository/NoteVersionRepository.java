package com.ainote.app.repository;

import com.ainote.app.entity.NoteVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteVersionRepository extends JpaRepository<NoteVersion, String> {

    List<NoteVersion> findByNoteIdOrderByCreatedAtDesc(String noteId);

    List<NoteVersion> findByNoteIdOrderByCreatedAtAsc(String noteId);

    long countByNoteId(String noteId);
}
