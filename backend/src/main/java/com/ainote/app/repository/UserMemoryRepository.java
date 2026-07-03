package com.ainote.app.repository;

import com.ainote.app.entity.UserMemory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户记忆 Repository
 */
@Repository
public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    /**
     * 按用户ID查询最近的消息（按时间倒序）
     */
    List<UserMemory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * 按用户ID查询最近的消息（按时间正序，用于构建对话历史）
     */
    @Query("SELECT m FROM UserMemory m WHERE m.userId = :userId ORDER BY m.createdAt ASC")
    List<UserMemory> findByUserIdOrderByCreatedAtAsc(String userId, Pageable pageable);

    @Query("""
        SELECT m FROM UserMemory m
        WHERE m.userId = :userId
          AND m.messageType IN ('USER', 'AI')
          AND (:beforeId IS NULL OR m.id < :beforeId)
        ORDER BY m.createdAt DESC, m.id DESC
        """)
    List<UserMemory> findVisibleByUserIdBeforeIdOrderByCreatedAtDesc(
            @Param("userId") String userId,
            @Param("beforeId") Long beforeId,
            Pageable pageable);

    /**
     * 统计用户的消息数量
     */
    long countByUserId(String userId);

    /**
     * 删除用户的所有消息
     */
    @Modifying
    @Transactional
    void deleteByUserId(String userId);

    /**
     * 删除用户超出限制的旧消息
     * 保留最新的 keepCount 条消息
     */
    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM user_memories
        WHERE user_id = :userId
        AND id NOT IN (
            SELECT id FROM (
                SELECT id FROM user_memories
                WHERE user_id = :userId
                ORDER BY created_at DESC
                LIMIT :keepCount
            ) AS recent
        )
        """, nativeQuery = true)
    void deleteOldMessages(String userId, int keepCount);

    /**
     * 获取用户最新的消息ID列表
     */
    @Query("SELECT m.id FROM UserMemory m WHERE m.userId = :userId ORDER BY m.createdAt DESC")
    List<Long> findIdsByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * 获取用户最新一条消息
     */
    @Query("SELECT m FROM UserMemory m WHERE m.userId = :userId ORDER BY m.createdAt DESC")
    List<UserMemory> findTopByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * 按用户ID查询所有消息（按 sequence_number 正序，用于 ReliableChatMemoryStore）
     * 兼容旧数据：sequence_number 为 null 的排在前面按 created_at 排
     */
    @Query("SELECT m FROM UserMemory m WHERE m.userId = :userId " +
           "ORDER BY COALESCE(m.sequenceNumber, 0) ASC, m.createdAt ASC")
    List<UserMemory> findAllByUserIdOrderByCreatedAtAsc(String userId);

    /**
     * 获取用户当前最大 sequence_number
     */
    @Query("SELECT COALESCE(MAX(m.sequenceNumber), 0) FROM UserMemory m WHERE m.userId = :userId")
    int findMaxSequenceNumber(String userId);

    /**
     * 删除用户 sequence_number 小于指定值的消息（前端裁剪时使用）
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM UserMemory m WHERE m.userId = :userId AND m.sequenceNumber < :seqNum")
    void deleteBeforeSequence(String userId, int seqNum);
}
