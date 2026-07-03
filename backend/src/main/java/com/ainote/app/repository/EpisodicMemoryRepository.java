package com.ainote.app.repository;

import com.ainote.app.entity.EpisodicMemory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EpisodicMemoryRepository extends JpaRepository<EpisodicMemory, Long> {

    /**
     * 获取用户最近的会话摘要
     */
    @Query("SELECT m FROM EpisodicMemory m WHERE m.userId = :userId " +
           "AND (m.status IS NULL OR m.status = 'active') ORDER BY m.createdAt DESC")
    List<EpisodicMemory> findRecentByUserId(String userId, Pageable pageable);

    /**
     * 统计用户的情节记忆数量
     */
    long countByUserId(String userId);
}
