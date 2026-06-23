package com.ainote.app.repository;

import com.ainote.app.entity.NoteVersion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteVersionRepository extends JpaRepository<NoteVersion, String> {

    List<NoteVersion> findByNoteIdOrderByCreatedAtDesc(String noteId);

    List<NoteVersion> findByNoteIdOrderByCreatedAtAsc(String noteId);

    @Query("SELECT nv.id FROM NoteVersion nv WHERE nv.note.id = :noteId ORDER BY nv.createdAt ASC")
    List<String> findOldestIdsByNoteId(@Param("noteId") String noteId, Pageable pageable);

    long countByNoteId(String noteId);
}
