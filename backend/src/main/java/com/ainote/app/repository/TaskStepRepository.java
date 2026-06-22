package com.ainote.app.repository;

import com.ainote.app.entity.TaskStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface TaskStepRepository extends JpaRepository<TaskStep, String> {
    List<TaskStep> findByPlanIdOrderByStepOrder(String planId);
    Optional<TaskStep> findByIdAndPlanId(String id, String planId);
    List<TaskStep> findByPlanIdAndStatusOrderByStepOrder(String planId, String status);
    long countByPlanIdAndStatus(String planId, String status);

    @Modifying
    @Transactional
    @Query("UPDATE TaskStep s SET s.stepOrder = s.stepOrder + 1 WHERE s.planId = :planId AND s.stepOrder >= :afterOrder")
    void shiftStepOrders(String planId, int afterOrder);

    // Global aggregate queries for admin metrics
    long countByStatus(String status);
    long count();
}
