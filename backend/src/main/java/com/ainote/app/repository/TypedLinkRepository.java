package com.ainote.app.repository;

import com.ainote.app.entity.TypedLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TypedLinkRepository extends JpaRepository<TypedLink, String> {
    List<TypedLink> findBySourceNoteId(String sourceNoteId);
    List<TypedLink> findByTargetNoteId(String targetNoteId);
    List<TypedLink> findBySourceNoteIdOrTargetNoteId(String sourceNoteId, String targetNoteId);
    List<TypedLink> findByRelationType(String relationType);

    @Query("""
            SELECT tl FROM TypedLink tl, Note source, Note target
            WHERE source.id = tl.sourceNoteId
              AND target.id = tl.targetNoteId
              AND source.user.id = :userId
              AND target.user.id = :userId
              AND source.deletedAt IS NULL
              AND target.deletedAt IS NULL
              AND (tl.sourceNoteId = :noteId OR tl.targetNoteId = :noteId)
            """)
    List<TypedLink> findOwnedByNoteId(@Param("noteId") String noteId, @Param("userId") String userId);

    @Query("""
            SELECT tl FROM TypedLink tl, Note source, Note target
            WHERE source.id = tl.sourceNoteId
              AND target.id = tl.targetNoteId
              AND source.user.id = :userId
              AND target.user.id = :userId
              AND source.deletedAt IS NULL
              AND target.deletedAt IS NULL
              AND tl.relationType = :relationType
            """)
    List<TypedLink> findOwnedByRelationType(@Param("relationType") String relationType, @Param("userId") String userId);

    @Query("""
            SELECT tl FROM TypedLink tl, Note source, Note target
            WHERE source.id = tl.sourceNoteId
              AND target.id = tl.targetNoteId
              AND source.user.id = :userId
              AND target.user.id = :userId
              AND source.deletedAt IS NULL
              AND target.deletedAt IS NULL
              AND tl.id = :id
            """)
    Optional<TypedLink> findOwnedById(@Param("id") String id, @Param("userId") String userId);
}
