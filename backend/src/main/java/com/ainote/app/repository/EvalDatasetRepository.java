package com.ainote.app.repository;

import com.ainote.app.entity.EvalDataset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EvalDatasetRepository extends JpaRepository<EvalDataset, String> {
    List<EvalDataset> findByUserIdOrderByUpdatedAtDesc(String userId);
    Optional<EvalDataset> findByIdAndUserId(String id, String userId);
}
