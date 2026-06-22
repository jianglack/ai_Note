package com.ainote.note.repository;

import com.ainote.note.entity.Note;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NoteRepository extends JpaRepository<Note, String> {

    List<Note> findByDeletedAtIsNull();
    List<Note> findByDeletedAtIsNotNull();
    List<Note> findByUserIdAndDeletedAtIsNull(String userId);
    List<Note> findByUserIdAndDeletedAtIsNotNull(String userId);
    List<Note> findByFolderIdAndDeletedAtIsNull(String folderId);
    List<Note> findByFolderId(String folderId);
    Optional<Note> findByIdAndDeletedAtIsNull(String id);
    Optional<Note> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    @Query("SELECT n FROM Note n LEFT JOIN FETCH n.tags LEFT JOIN FETCH n.folder WHERE n.id = :id AND n.userId = :userId AND n.deletedAt IS NULL")
    Optional<Note> findByIdAndUserIdWithTagsAndFolder(@Param("id") String id, @Param("userId") String userId);

    @Query("SELECT DISTINCT n FROM Note n LEFT JOIN FETCH n.tags WHERE n.userId = :userId AND n.deletedAt IS NULL")
    List<Note> findByUserIdWithTags(@Param("userId") String userId);

    @Query("SELECT n FROM Note n WHERE n.deletedAt IS NULL AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(n.content) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Note> searchNotes(@Param("query") String query);

    @Query("SELECT n FROM Note n WHERE n.userId = :userId AND n.deletedAt IS NULL AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(n.content) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Note> searchByUserIdAndQuery(@Param("userId") String userId, @Param("query") String query);

    @Query("SELECT n FROM Note n JOIN n.tags t WHERE t.id = :tagId AND n.deletedAt IS NULL")
    List<Note> findByTagId(@Param("tagId") String tagId);

    @Query("SELECT n FROM Note n WHERE n.userId = :userId AND n.title = :title AND n.deletedAt IS NULL AND n.createdAt > :after ORDER BY n.createdAt DESC")
    List<Note> findRecentByUserIdAndTitle(@Param("userId") String userId, @Param("title") String title, @Param("after") LocalDateTime after);

    Page<Note> findByDeletedAtIsNull(Pageable pageable);
}
