package com.ainote.note.repository;

import com.ainote.note.entity.NoteVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteVersionRepository extends JpaRepository<NoteVersion, String> {

    List<NoteVersion> findByNoteIdOrderByCreatedAtDesc(String noteId);

    long countByNoteId(String noteId);
}
