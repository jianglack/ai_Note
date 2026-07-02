package com.ainote.app.repository;

import com.ainote.app.entity.SemanticMemory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface SemanticMemoryRepository extends JpaRepository<SemanticMemory, Long> {

    /**
     * 获取用户的语义记忆，按衰减分数排序（decay_score 由应用层定期更新），取 top N
     */
    @Query("SELECT m FROM SemanticMemory m WHERE m.userId = :userId " +
           "ORDER BY m.decayScore DESC")
    List<SemanticMemory> findTopByUserId(String userId, Pageable pageable);

    /**
     * 获取用户某分类的所有语义记忆
     */
    List<SemanticMemory> findByUserIdAndCategory(String userId, String category);

    /**
     * 获取用户所有语义记忆
     */
    List<SemanticMemory> findByUserId(String userId);

    /**
     * 精确匹配去重（保留作为后备）
     */
    @Query("SELECT m FROM SemanticMemory m WHERE m.userId = :userId AND m.content = :content")
    List<SemanticMemory> findByUserIdAndContent(String userId, String content);

    /**
     * 向量相似度搜索：找到与给定 embedding 最相似的记忆
     * 使用 cosine 距离，1 - distance = similarity，取 similarity > threshold 的结果
     * 注意：pgvector 的 <=> 运算符返回的是 cosine distance（0=完全相同，2=完全相反）
     */
    @Query(value = """
        SELECT * FROM semantic_memories
        WHERE user_id = :userId
          AND embedding IS NOT NULL
          AND (1 - (embedding <=> cast(:embedding AS vector))) > :threshold
        ORDER BY embedding <=> cast(:embedding AS vector) ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<SemanticMemory> findSimilarByEmbedding(String userId, String embedding, double threshold, int limit);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE semantic_memories
        SET embedding = cast(:embedding AS vector)
        WHERE id = :id
        """, nativeQuery = true)
    void updateEmbedding(Long id, String embedding);

    /**
     * 统计用户的语义记忆数量
     */
    long countByUserId(String userId);

    /**
     * 获取用户衰减分数最低的记忆（用于容量淘汰）
     */
    @Query(value = """
        SELECT * FROM semantic_memories
        WHERE user_id = :userId
        ORDER BY decay_score ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<SemanticMemory> findLowestScored(String userId, int limit);

    /**
     * 批量删除指定 ID 的记忆
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM SemanticMemory m WHERE m.id IN :ids")
    void deleteByIds(List<Long> ids);

    /**
     * 获取用户所有有 embedding 的记忆（用于批量衰减分数更新）
     */
    @Query(value = """
        SELECT * FROM semantic_memories
        WHERE user_id = :userId
          AND embedding IS NOT NULL
        """, nativeQuery = true)
    List<SemanticMemory> findByUserIdWithEmbedding(String userId);
}
