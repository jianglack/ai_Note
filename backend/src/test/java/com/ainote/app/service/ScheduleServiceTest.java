package com.ainote.app.service;

import com.ainote.app.entity.Note;
import com.ainote.app.entity.Schedule;
import com.ainote.app.entity.User;
import com.ainote.app.model.ScheduleRequest;
import com.ainote.app.model.ScheduleResponse;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.ScheduleRepository;
import com.ainote.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
@DisplayName("ScheduleService 单元测试")
class ScheduleServiceTest {
    private ScheduleRepository scheduleRepository;
    private NoteRepository noteRepository;
    private UserRepository userRepository;
    private KnowledgeGraphService knowledgeGraphService;
    private ScheduleService scheduleService;

    private User testUser;
    private Schedule testSchedule;

    @BeforeEach
    void setUp() {
        scheduleRepository = mock(ScheduleRepository.class);
        noteRepository = mock(NoteRepository.class);
        userRepository = mock(UserRepository.class);
        knowledgeGraphService = mock(KnowledgeGraphService.class);
        scheduleService = new ScheduleService(scheduleRepository, noteRepository, userRepository, knowledgeGraphService);

        testUser = new User();
        testUser.setId("user-123");
        testUser.setUsername("testuser");

        testSchedule = new Schedule();
        testSchedule.setId("schedule-456");
        testSchedule.setTitle("Test Meeting");
        testSchedule.setDescription("Test description");
        testSchedule.setUser(testUser);
        testSchedule.setStartTime(LocalDateTime.now().plusHours(1));
        testSchedule.setEndTime(LocalDateTime.now().plusHours(2));
        testSchedule.setStatus("pending");
        testSchedule.setAllDay(false);
        testSchedule.setCreatedAt(LocalDateTime.now());
        testSchedule.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("getSchedules 应返回用户的日程列表")
    void shouldGetSchedules() {
        when(scheduleRepository.findByUserIdOrderByStartTimeDesc("user-123"))
            .thenReturn(List.of(testSchedule));

        List<ScheduleResponse> result = scheduleService.getSchedules("user-123", null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Test Meeting");
    }

    @Test
    @DisplayName("getSchedules 应支持日期范围过滤")
    void shouldGetSchedulesWithDateRange() {
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = LocalDateTime.now().plusDays(7);

        when(scheduleRepository.findByUserIdAndDateRange("user-123", start, end))
            .thenReturn(List.of(testSchedule));

        List<ScheduleResponse> result = scheduleService.getSchedules("user-123", start, end);

        assertThat(result).hasSize(1);
        verify(scheduleRepository).findByUserIdAndDateRange("user-123", start, end);
    }

    @Test
    @DisplayName("getSchedule 应返回指定日程")
    void shouldGetScheduleById() {
        when(scheduleRepository.findById("schedule-456")).thenReturn(Optional.of(testSchedule));

        ScheduleResponse result = scheduleService.getSchedule("schedule-456", "user-123");

        assertThat(result.getId()).isEqualTo("schedule-456");
        assertThat(result.getTitle()).isEqualTo("Test Meeting");
    }

    @Test
    @DisplayName("getSchedule 不存在时应抛出异常")
    void shouldThrowWhenScheduleNotFound() {
        when(scheduleRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.getSchedule("nonexistent", "user-123"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Schedule not found");
    }

    @Test
    @DisplayName("getSchedule 访问他人日程应抛出异常")
    void shouldThrowWhenAccessDenied() {
        when(scheduleRepository.findById("schedule-456")).thenReturn(Optional.of(testSchedule));

        assertThatThrownBy(() -> scheduleService.getSchedule("schedule-456", "other-user"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Access denied");
    }

    @Test
    @DisplayName("createSchedule 应创建新日程")
    void shouldCreateSchedule() {
        ScheduleRequest request = new ScheduleRequest();
        request.setTitle("New Meeting");
        request.setDescription("New description");
        request.setStartTime(LocalDateTime.now().plusDays(1));
        request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
        request.setAllDay(false);

        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));

        ScheduleResponse result = scheduleService.createSchedule(request, "user-123");

        assertThat(result.getTitle()).isEqualTo("New Meeting");

        ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("pending");
    }

    @Test
    @DisplayName("createSchedule 应关联笔记")
    void shouldCreateScheduleWithNotes() {
        Note note = new Note();
        note.setId("note-1");
        note.setTitle("Related note");
        note.setUser(testUser);

        ScheduleRequest request = new ScheduleRequest();
        request.setTitle("Meeting with notes");
        request.setStartTime(LocalDateTime.now().plusDays(1));
        request.setNoteIds(List.of("note-1"));

        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));
        when(noteRepository.findAllById(List.of("note-1"))).thenReturn(List.of(note));
        ScheduleResponse response = scheduleService.createSchedule(request, "user-123");

        ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Meeting with notes");
        assertThat(captor.getValue().getNotes()).extracting(Note::getId).containsExactly("note-1");
        assertThat(response.getNotes()).hasSize(1);
        assertThat(response.getNotes().get(0).getId()).isEqualTo("note-1");
    }

    @Test
    @DisplayName("createSchedule 用户不存在应抛出异常")
    void shouldThrowWhenUserNotFoundOnCreate() {
        ScheduleRequest request = new ScheduleRequest();
        request.setTitle("Test");
        request.setStartTime(LocalDateTime.now());

        when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.createSchedule(request, "nonexistent"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("User not found");
    }

    @Test
    @DisplayName("updateSchedule 应更新日程")
    void shouldUpdateSchedule() {
        ScheduleRequest request = new ScheduleRequest();
        request.setTitle("Updated Meeting");
        request.setDescription("Updated description");
        request.setStartTime(testSchedule.getStartTime());
        request.setEndTime(testSchedule.getEndTime());

        when(scheduleRepository.findById("schedule-456")).thenReturn(Optional.of(testSchedule));

        ScheduleResponse result = scheduleService.updateSchedule("schedule-456", request, "user-123");

        assertThat(result.getTitle()).isEqualTo("Updated Meeting");
        verify(scheduleRepository).save(any(Schedule.class));
    }

    @Test
    @DisplayName("updateSchedule 时间改变且已完成应重置状态")
    void shouldResetStatusWhenTimeChangedAndCompleted() {
        testSchedule.setStatus("completed");

        ScheduleRequest request = new ScheduleRequest();
        request.setTitle("Test");
        request.setStartTime(LocalDateTime.now().plusDays(5)); // 时间改变
        request.setEndTime(LocalDateTime.now().plusDays(5).plusHours(1));

        when(scheduleRepository.findById("schedule-456")).thenReturn(Optional.of(testSchedule));

        scheduleService.updateSchedule("schedule-456", request, "user-123");

        ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("pending");
    }

    @Test
    @DisplayName("deleteSchedule 应删除日程")
    void shouldDeleteSchedule() {
        when(scheduleRepository.findById("schedule-456")).thenReturn(Optional.of(testSchedule));

        scheduleService.deleteSchedule("schedule-456", "user-123");

        verify(scheduleRepository).delete(testSchedule);
    }

    @Test
    @DisplayName("deleteSchedule 他人日程应抛出异常")
    void shouldThrowWhenDeleteOtherUserSchedule() {
        when(scheduleRepository.findById("schedule-456")).thenReturn(Optional.of(testSchedule));

        assertThatThrownBy(() -> scheduleService.deleteSchedule("schedule-456", "other-user"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Access denied");
    }

    @Test
    @DisplayName("updateStatus 应更新状态")
    void shouldUpdateStatus() {
        when(scheduleRepository.findById("schedule-456")).thenReturn(Optional.of(testSchedule));

        ScheduleResponse result = scheduleService.updateStatus("schedule-456", "completed", "user-123");

        assertThat(result.getStatus()).isEqualTo("completed");

        ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("completed");
    }

    @Test
    void shouldRejectInvalidStatus() {
        assertThatThrownBy(() -> scheduleService.updateStatus("schedule-456", "owned", "user-123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid schedule status");

        verify(scheduleRepository, never()).save(any(Schedule.class));
    }

    @Test
    @DisplayName("computeStatus 应计算正确的状态")
    void shouldComputeCorrectStatus() {
        // 测试 pending 状态（未来的日程）
        testSchedule.setStartTime(LocalDateTime.now().plusHours(1));
        testSchedule.setEndTime(LocalDateTime.now().plusHours(2));
        testSchedule.setStatus("pending");

        when(scheduleRepository.findById("schedule-456")).thenReturn(Optional.of(testSchedule));

        ScheduleResponse result = scheduleService.getSchedule("schedule-456", "user-123");
        assertThat(result.getStatus()).isEqualTo("pending");
    }

    @Test
    @DisplayName("computeStatus 已完成日程应保持 completed")
    void shouldKeepCompletedStatus() {
        testSchedule.setStatus("completed");

        when(scheduleRepository.findById("schedule-456")).thenReturn(Optional.of(testSchedule));

        ScheduleResponse result = scheduleService.getSchedule("schedule-456", "user-123");
        assertThat(result.getStatus()).isEqualTo("completed");
    }
}
