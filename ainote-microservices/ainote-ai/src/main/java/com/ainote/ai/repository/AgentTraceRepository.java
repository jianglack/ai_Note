package com.ainote.ai.repository;

import com.ainote.ai.entity.AgentTrace;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AgentTraceRepository extends JpaRepository<AgentTrace, String> {

    List<AgentTrace> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    long countByUserId(String userId);

    @Query("SELECT COALESCE(SUM(t.totalTokens), 0) FROM AgentTrace t WHERE t.userId = :userId")
    long sumTotalTokensByUserId(String userId);

    long countByUserIdAndCreatedAtBetween(String userId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT COALESCE(SUM(t.totalTokens), 0) FROM AgentTrace t WHERE t.userId = :userId AND t.createdAt BETWEEN :start AND :end")
    long sumTotalTokensByUserIdAndDateRange(String userId, LocalDateTime start, LocalDateTime end);
}
