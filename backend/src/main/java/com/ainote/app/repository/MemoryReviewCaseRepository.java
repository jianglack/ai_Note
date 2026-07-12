package com.ainote.app.repository;

import com.ainote.app.entity.MemoryReviewCase;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemoryReviewCaseRepository extends JpaRepository<MemoryReviewCase, Long> {

    List<MemoryReviewCase> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    List<MemoryReviewCase> findByStatusOrderByReviewedAtDesc(String status, Pageable pageable);
}
