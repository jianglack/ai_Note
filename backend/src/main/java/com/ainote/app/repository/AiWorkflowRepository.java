package com.ainote.app.repository;

import com.ainote.app.entity.AiWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AiWorkflowRepository extends JpaRepository<AiWorkflow, String> {
    List<AiWorkflow> findByUserIdOrderByUpdatedAtDesc(String userId);
    List<AiWorkflow> findByTriggerTypeAndEnabledTrue(String triggerType);
    Optional<AiWorkflow> findByIdAndUserId(String id, String userId);
}
