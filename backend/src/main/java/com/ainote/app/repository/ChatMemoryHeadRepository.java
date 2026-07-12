package com.ainote.app.repository;

import com.ainote.app.entity.ChatMemoryHead;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatMemoryHeadRepository extends JpaRepository<ChatMemoryHead, String> {

    @Modifying
    @Query(value = """
            INSERT INTO chat_memory_heads(user_id, next_sequence_number, last_compaction_enqueued_sequence)
            SELECT :userId, COALESCE(MAX(sequence_number), -1) + 1, -1
            FROM user_memories
            WHERE user_id = :userId
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    void ensureExists(@Param("userId") String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM ChatMemoryHead h WHERE h.userId = :userId")
    Optional<ChatMemoryHead> findByUserIdForUpdate(@Param("userId") String userId);
}
