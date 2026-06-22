package com.ainote.app.repository;

import com.ainote.app.entity.AiWorkflowRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AiWorkflowRunRepository extends JpaRepository<AiWorkflowRun, String> {
    List<AiWorkflowRun> findByWorkflowIdOrderByStartedAtDesc(String workflowId);
}
