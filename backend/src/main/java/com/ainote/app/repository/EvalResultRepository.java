package com.ainote.app.repository;

import com.ainote.app.entity.EvalResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EvalResultRepository extends JpaRepository<EvalResult, String> {
    List<EvalResult> findByRunId(String runId);
}
