package com.ainote.ai.repository;

import com.ainote.ai.entity.UserMemory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    List<UserMemory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    @Query("SELECT m FROM UserMemory m WHERE m.userId = :userId ORDER BY m.createdAt ASC")
    List<UserMemory> findByUserIdOrderByCreatedAtAsc(String userId, Pageable pageable);

    long countByUserId(String userId);

    @Modifying
    @Transactional
    void deleteByUserId(String userId);

    @Query("SELECT m FROM UserMemory m WHERE m.userId = :userId ORDER BY m.createdAt ASC")
    List<UserMemory> findAllByUserIdOrderByCreatedAtAsc(String userId);

    @Query("SELECT m FROM UserMemory m WHERE m.userId = :userId ORDER BY m.createdAt DESC")
    List<UserMemory> findTopByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
