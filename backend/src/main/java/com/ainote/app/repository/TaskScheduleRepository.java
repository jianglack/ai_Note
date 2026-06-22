package com.ainote.app.repository;

import com.ainote.app.entity.TaskSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskScheduleRepository extends JpaRepository<TaskSchedule, String> {
    List<TaskSchedule> findByUserIdOrderByCreatedAtDesc(String userId);

    @Query("SELECT s FROM TaskSchedule s WHERE s.enabled = true AND s.nextRunAt <= :now")
    List<TaskSchedule> findDueSchedules(LocalDateTime now);
}
