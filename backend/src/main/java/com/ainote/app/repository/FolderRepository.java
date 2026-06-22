package com.ainote.app.repository;

import com.ainote.app.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FolderRepository extends JpaRepository<Folder, String> {

    List<Folder> findByParentId(String parentId);
    List<Folder> findByParentIdIsNull();

    @Query("SELECT f FROM Folder f WHERE f.id = :id AND f.user.id = :userId")
    Optional<Folder> findByIdAndUserId(@Param("id") String id, @Param("userId") String userId);

    @Query("SELECT f FROM Folder f WHERE f.parentId = :parentId AND f.user.id = :userId")
    List<Folder> findByParentIdAndUserId(@Param("parentId") String parentId, @Param("userId") String userId);

    @Query("SELECT f FROM Folder f WHERE f.user.id = :userId")
    List<Folder> findByUserId(@Param("userId") String userId);

    @Query("SELECT f FROM Folder f WHERE f.user.id = :userId AND f.parentId IS NULL")
    List<Folder> findByUserIdAndParentIdIsNull(@Param("userId") String userId);

    @Query("SELECT f FROM Folder f WHERE f.user.id = :userId AND f.name = :name")
    List<Folder> findByUserIdAndName(@Param("userId") String userId, @Param("name") String name);
}
