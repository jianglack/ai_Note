package com.ainote.app.repository;

import com.ainote.app.entity.Note;
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
    List<Note> findByFolderId(String folderId);  // 包括回收站的笔记
    Optional<Note> findByIdAndDeletedAtIsNull(String id);

    @Query("SELECT n FROM Note n WHERE n.id = :id AND n.user.id = :userId")
    Optional<Note> findByIdAndUserId(@Param("id") String id, @Param("userId") String userId);

    Optional<Note> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    @Query("SELECT n FROM Note n WHERE n.folder.id = :folderId AND n.user.id = :userId")
    List<Note> findByFolderIdAndUserId(@Param("folderId") String folderId, @Param("userId") String userId);

    @Query("SELECT n FROM Note n WHERE n.folder.id = :folderId AND n.user.id = :userId AND n.deletedAt IS NULL")
    List<Note> findByFolderIdAndUserIdAndDeletedAtIsNull(@Param("folderId") String folderId, @Param("userId") String userId);
    
    // 带 JOIN FETCH 的查询，用于避免懒加载问题
    @Query("SELECT n FROM Note n LEFT JOIN FETCH n.tags LEFT JOIN FETCH n.folder WHERE n.id = :id AND n.user.id = :userId AND n.deletedAt IS NULL")
    Optional<Note> findByIdAndUserIdWithTagsAndFolder(@Param("id") String id, @Param("userId") String userId);

    @Query("SELECT DISTINCT n FROM Note n LEFT JOIN FETCH n.tags WHERE n.user.id = :userId AND n.deletedAt IS NULL")
    List<Note> findByUserIdWithTags(@Param("userId") String userId);

    @Query("SELECT DISTINCT n FROM Note n LEFT JOIN FETCH n.tags LEFT JOIN FETCH n.folder WHERE n.user.id = :userId AND n.deletedAt IS NULL")
    List<Note> findByUserIdWithTagsAndFolder(@Param("userId") String userId);

    @Query(
            value = "SELECT n.id FROM Note n WHERE n.user.id = :userId AND n.deletedAt IS NULL ORDER BY n.updatedAt DESC",
            countQuery = "SELECT COUNT(n) FROM Note n WHERE n.user.id = :userId AND n.deletedAt IS NULL"
    )
    Page<String> findNoteIdsByUserId(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT DISTINCT n FROM Note n LEFT JOIN FETCH n.tags LEFT JOIN FETCH n.folder WHERE n.id IN :ids AND n.user.id = :userId AND n.deletedAt IS NULL")
    List<Note> findByIdsWithTagsAndFolder(@Param("ids") List<String> ids, @Param("userId") String userId);

    @Query("SELECT n FROM Note n WHERE n.deletedAt IS NULL AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(n.content) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Note> searchNotes(@Param("query") String query);

    @Query("SELECT n FROM Note n WHERE n.user.id = :userId AND n.deletedAt IS NULL AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(n.content) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Note> searchByUserIdAndQuery(@Param("userId") String userId, @Param("query") String query);

    @Query("SELECT n FROM Note n JOIN n.tags t WHERE t.id = :tagId AND n.deletedAt IS NULL")
    List<Note> findByTagId(@Param("tagId") String tagId);

    @Query("SELECT n FROM Note n WHERE n.user.id = :userId AND n.title = :title AND n.deletedAt IS NULL AND n.createdAt > :after ORDER BY n.createdAt DESC")
    List<Note> findRecentByUserIdAndTitle(@Param("userId") String userId, @Param("title") String title, @Param("after") LocalDateTime after);

    @Query("SELECT n FROM Note n WHERE n.user.id = :userId AND n.deletedAt IS NULL ORDER BY n.updatedAt DESC")
    List<Note> findRecentByUserId(@Param("userId") String userId, Pageable pageable);

    long countByUserIdAndDeletedAtIsNull(String userId);
}
