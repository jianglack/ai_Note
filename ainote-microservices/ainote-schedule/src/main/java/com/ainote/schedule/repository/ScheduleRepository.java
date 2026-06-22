package com.ainote.schedule.repository;

import com.ainote.schedule.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduleRepository extends JpaRepository<Schedule, String> {

    List<Schedule> findByUserIdOrderByStartTimeDesc(String userId);

    @Query("SELECT s FROM Schedule s WHERE s.userId = :userId " +
           "AND s.startTime >= :startDate AND s.startTime <= :endDate " +
           "ORDER BY s.startTime")
    List<Schedule> findByUserIdAndDateRange(
        @Param("userId") String userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT s FROM Schedule s WHERE s.userId = :userId " +
           "AND s.startTime >= :startOfDay AND s.startTime <= :endOfDay " +
           "ORDER BY s.startTime")
    List<Schedule> findTodaySchedules(
        @Param("userId") String userId,
        @Param("startOfDay") LocalDateTime startOfDay,
        @Param("endOfDay") LocalDateTime endOfDay
    );

    @Query("SELECT s FROM Schedule s WHERE s.userId = :userId " +
           "AND s.startTime > :now " +
           "ORDER BY s.startTime")
    List<Schedule> findUpcoming(
        @Param("userId") String userId,
        @Param("now") LocalDateTime now
    );

    @Query("SELECT s FROM Schedule s WHERE s.userId = :userId " +
           "AND s.startTime >= :startDate AND s.startTime <= :endDate " +
           "AND s.reminderMinutes IS NOT NULL " +
           "ORDER BY s.startTime")
    List<Schedule> findUpcomingWithReminder(
        @Param("userId") String userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT s FROM Schedule s WHERE s.userId = :userId AND s.title = :title AND s.startTime = :startTime")
    List<Schedule> findByUserIdAndTitleAndStartTime(
        @Param("userId") String userId,
        @Param("title") String title,
        @Param("startTime") LocalDateTime startTime
    );
}
