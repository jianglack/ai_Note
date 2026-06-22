package com.ainote.app.repository;

import com.ainote.app.entity.RagFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RagFeedbackRepository extends JpaRepository<RagFeedback, Long> {

    @Query("SELECT f FROM RagFeedback f WHERE f.createdAt > :since ORDER BY f.createdAt DESC")
    List<RagFeedback> findRecentFeedback(@Param("since") LocalDateTime since);

    @Query("SELECT f FROM RagFeedback f WHERE f.userId = :userId AND f.createdAt > :since ORDER BY f.createdAt DESC")
    List<RagFeedback> findRecentByUser(@Param("userId") String userId, @Param("since") LocalDateTime since);
}
