package com.ainote.app.repository;

import com.ainote.app.entity.EpisodicMemory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EpisodicMemoryRepository extends JpaRepository<EpisodicMemory, Long> {

    interface MemorySimilarityView {
        Long getId();
        Double getSemanticSimilarity();
    }

    record MemorySimilarity(Long id, Double semanticSimilarity) implements MemorySimilarityView {
        @Override
        public Long getId() {
            return id;
        }

        @Override
        public Double getSemanticSimilarity() {
            return semanticSimilarity;
        }
    }

    /**
     * 获取用户最近的会话摘要
     */
    @Query("SELECT m FROM EpisodicMemory m WHERE m.userId = :userId " +
           "AND (m.status IS NULL OR m.status = 'active') ORDER BY m.createdAt DESC")
    List<EpisodicMemory> findRecentByUserId(String userId, Pageable pageable);

    @Query("SELECT m FROM EpisodicMemory m WHERE m.userId = :userId AND m.id IN :ids " +
           "AND (m.status IS NULL OR m.status = 'active')")
    List<EpisodicMemory> findActiveByUserIdAndIdIn(String userId, List<Long> ids);

    @Query(value = """
        SELECT id AS id,
               (1 - (summary_embedding <=> cast(:embedding AS vector))) AS "semanticSimilarity"
        FROM episodic_memories
        WHERE user_id = :userId
          AND (status IS NULL OR status = 'active')
          AND summary_embedding IS NOT NULL
          AND (1 - (summary_embedding <=> cast(:embedding AS vector))) > :threshold
        ORDER BY summary_embedding <=> cast(:embedding AS vector) ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<MemorySimilarityView> findRelevantEpisodicMatches(String userId,
                                                           String embedding,
                                                           double threshold,
                                                           int limit);

    /**
     * 统计用户的情节记忆数量
     */
    long countByUserId(String userId);

    boolean existsByUserIdAndSourceMessageRange(String userId, String sourceMessageRange);
}
