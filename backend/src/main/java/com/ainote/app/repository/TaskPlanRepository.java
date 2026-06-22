package com.ainote.app.repository;

import com.ainote.app.entity.TaskPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskPlanRepository extends JpaRepository<TaskPlan, String> {
    List<TaskPlan> findByUserIdOrderByCreatedAtDesc(String userId);
    List<TaskPlan> findByUserIdAndStatusIn(String userId, List<String> statuses);
    long countByUserIdAndStatusIn(String userId, List<String> statuses);

    // Global aggregate queries for admin metrics
    long countByStatus(String status);
    long count();
}
