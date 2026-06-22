package com.ainote.app.repository;

import com.ainote.app.entity.EvalRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EvalRunRepository extends JpaRepository<EvalRun, String> {
    List<EvalRun> findByUserIdOrderByStartedAtDesc(String userId);
    List<EvalRun> findByDatasetIdOrderByStartedAtDesc(String datasetId);
    Optional<EvalRun> findByIdAndUserId(String id, String userId);
}
