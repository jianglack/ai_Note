package com.ainote.note.repository;

import com.ainote.note.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FolderRepository extends JpaRepository<Folder, String> {

    List<Folder> findByParentId(String parentId);
    List<Folder> findByParentIdIsNull();

    @Query("SELECT f FROM Folder f WHERE f.userId = :userId")
    List<Folder> findByUserId(@Param("userId") String userId);

    @Query("SELECT f FROM Folder f WHERE f.userId = :userId AND f.parentId IS NULL")
    List<Folder> findByUserIdAndParentIdIsNull(@Param("userId") String userId);

    @Query("SELECT f FROM Folder f WHERE f.userId = :userId AND f.name = :name")
    List<Folder> findByUserIdAndName(@Param("userId") String userId, @Param("name") String name);
}
