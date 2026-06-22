package com.ainote.app.repository;

import com.ainote.app.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface ScheduleRepository extends JpaRepository<Schedule, String> {

    List<Schedule> findByUserIdOrderByStartTimeDesc(String userId);

    @Query("SELECT s FROM Schedule s JOIN FETCH s.user " +
           "WHERE s.status = 'pending' AND s.startTime <= :soonThreshold " +
           "ORDER BY s.startTime ASC")
    List<Schedule> findPendingDueOrSoon(@Param("soonThreshold") LocalDateTime soonThreshold);

    @Query("SELECT s FROM Schedule s WHERE s.user.id = :userId " +
           "AND s.startTime >= :startDate AND s.startTime <= :endDate " +
           "ORDER BY s.startTime")
    List<Schedule> findByUserIdAndDateRange(
        @Param("userId") String userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT s FROM Schedule s WHERE s.user.id = :userId " +
           "AND s.startTime >= :startDate AND s.startTime <= :endDate " +
           "AND s.reminderMinutes IS NOT NULL " +
           "ORDER BY s.startTime")
    List<Schedule> findUpcomingWithReminder(
        @Param("userId") String userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT s FROM Schedule s WHERE s.user.id = :userId AND s.title = :title AND s.startTime = :startTime")
    List<Schedule> findByUserIdAndTitleAndStartTime(@Param("userId") String userId, @Param("title") String title, @Param("startTime") LocalDateTime startTime);

    @Query("SELECT COUNT(s) FROM Schedule s WHERE s.user.id = :userId AND s.status = 'pending'")
    long countPendingByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(s) FROM Schedule s WHERE s.user.id = :userId AND s.status = 'pending' AND s.startTime < :now")
    long countOverdueByUserId(@Param("userId") String userId, @Param("now") LocalDateTime now);
}
