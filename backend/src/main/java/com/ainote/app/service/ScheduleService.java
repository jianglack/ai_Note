package com.ainote.app.service;

import com.ainote.app.entity.Note;
import com.ainote.app.entity.Schedule;
import com.ainote.app.entity.User;
import com.ainote.app.model.ScheduleRequest;
import com.ainote.app.model.ScheduleResponse;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.ScheduleRepository;
import com.ainote.app.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ScheduleService {

    private static final Set<String> ALLOWED_STATUSES = Set.of("pending", "completed", "cancelled");

    private final ScheduleRepository scheduleRepository;
    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final KnowledgeGraphService knowledgeGraphService;

    public ScheduleService(ScheduleRepository scheduleRepository,
                          NoteRepository noteRepository,
                          UserRepository userRepository,
                          KnowledgeGraphService knowledgeGraphService) {
        this.scheduleRepository = scheduleRepository;
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.knowledgeGraphService = knowledgeGraphService;
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedules(String userId, LocalDateTime startDate, LocalDateTime endDate) {
        List<Schedule> schedules;
        if (startDate != null && endDate != null) {
            schedules = scheduleRepository.findByUserIdAndDateRange(userId, startDate, endDate);
        } else {
            schedules = scheduleRepository.findByUserIdOrderByStartTimeDesc(userId);
        }
        return schedules.stream()
            .map(s -> ScheduleResponse.from(s, computeStatus(s)))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ScheduleResponse getSchedule(String id, String userId) {
        Schedule schedule = scheduleRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Schedule not found"));
        if (!schedule.getUser().getId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }
        return ScheduleResponse.from(schedule, computeStatus(schedule));
    }

    @Transactional
    public ScheduleResponse createSchedule(ScheduleRequest req, String userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        Schedule schedule = new Schedule();
        schedule.setId(UUID.randomUUID().toString());
        schedule.setTitle(req.getTitle());
        schedule.setDescription(req.getDescription());
        schedule.setUser(user);
        schedule.setStartTime(req.getStartTime());
        schedule.setEndTime(req.getEndTime());
        schedule.setAllDay(req.getAllDay() != null ? req.getAllDay() : false);
        schedule.setRrule(req.getRrule());
        schedule.setReminderMinutes(req.getReminderMinutes());
        schedule.setStatus("pending");
        schedule.setCreatedAt(LocalDateTime.now());
        schedule.setUpdatedAt(LocalDateTime.now());

        applyNoteLinks(schedule, req.getNoteIds(), userId);

        scheduleRepository.save(schedule);
        afterCommit(() -> knowledgeGraphService.syncSchedule(schedule.getId()));
        return ScheduleResponse.from(schedule, computeStatus(schedule));
    }

    @Transactional
    public ScheduleResponse updateSchedule(String id, ScheduleRequest req, String userId) {
        Schedule schedule = scheduleRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Schedule not found"));
        if (!schedule.getUser().getId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        boolean timeChanged = !schedule.getStartTime().equals(req.getStartTime()) ||
            (req.getEndTime() != null && !req.getEndTime().equals(schedule.getEndTime()));

        schedule.setTitle(req.getTitle());
        schedule.setDescription(req.getDescription());
        schedule.setStartTime(req.getStartTime());
        schedule.setEndTime(req.getEndTime());
        schedule.setAllDay(req.getAllDay() != null ? req.getAllDay() : false);
        schedule.setRrule(req.getRrule());
        schedule.setReminderMinutes(req.getReminderMinutes());
        schedule.setUpdatedAt(LocalDateTime.now());

        // 如果时间改变且状态为 completed，重置状态
        if (timeChanged && "completed".equals(schedule.getStatus())) {
            schedule.setStatus("pending");
        }

        if (req.getNoteIds() != null) {
            applyNoteLinks(schedule, req.getNoteIds(), userId);
        }

        scheduleRepository.save(schedule);
        afterCommit(() -> knowledgeGraphService.syncSchedule(schedule.getId()));
        return ScheduleResponse.from(schedule, computeStatus(schedule));
    }

    @Transactional
    public void deleteSchedule(String id, String userId) {
        Schedule schedule = scheduleRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Schedule not found"));
        if (!schedule.getUser().getId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }
        scheduleRepository.delete(schedule);
        afterCommit(() -> knowledgeGraphService.deleteSchedule(id));
    }

    @Transactional
    public ScheduleResponse updateStatus(String id, String status, String userId) {
        String normalizedStatus = normalizeStatus(status);
        Schedule schedule = scheduleRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Schedule not found"));
        if (!schedule.getUser().getId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }
        schedule.setStatus(normalizedStatus);
        schedule.setUpdatedAt(LocalDateTime.now());
        scheduleRepository.save(schedule);
        afterCommit(() -> knowledgeGraphService.syncSchedule(id));
        return ScheduleResponse.from(schedule, computeStatus(schedule));
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toLowerCase();
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Invalid schedule status");
        }
        return normalized;
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void applyNoteLinks(Schedule schedule, List<String> noteIds, String userId) {
        schedule.getNotes().clear();
        if (noteIds == null || noteIds.isEmpty()) {
            return;
        }
        List<Note> notes = noteRepository.findAllById(noteIds);
        notes.stream()
                .filter(note -> note.getUser() != null && userId.equals(note.getUser().getId()))
                .forEach(schedule.getNotes()::add);
    }

    private String computeStatus(Schedule schedule) {
        if ("completed".equals(schedule.getStatus()) || "cancelled".equals(schedule.getStatus())) {
            return schedule.getStatus();
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = schedule.getEndTime() != null ? schedule.getEndTime() : schedule.getStartTime();
        if (now.isAfter(endTime)) {
            return "expired";
        }
        if (now.isAfter(schedule.getStartTime()) || now.isEqual(schedule.getStartTime())) {
            return "in_progress";
        }
        return "pending";
    }
}
