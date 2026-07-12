package com.ainote.app.repository;

import com.ainote.app.entity.ChatMemoryCompactionJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMemoryCompactionJobRepository extends JpaRepository<ChatMemoryCompactionJob, Long> {

    boolean existsByUserIdAndFromSequenceAndToSequence(
            String userId, Integer fromSequence, Integer toSequence);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT j FROM ChatMemoryCompactionJob j
            WHERE ((j.status = 'pending' OR j.status = 'retry')
                    AND (j.nextAttemptAt IS NULL OR j.nextAttemptAt <= :now))
               OR (j.status = 'processing' AND j.leaseUntil < :now)
            ORDER BY j.createdAt ASC, j.id ASC
            """)
    List<ChatMemoryCompactionJob> findClaimable(
            @Param("now") LocalDateTime now, Pageable pageable);

    @Modifying
    @Query("DELETE FROM ChatMemoryCompactionJob j WHERE j.userId = :userId")
    void deleteByUserId(@Param("userId") String userId);
}
