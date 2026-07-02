package com.ainote.app.service;

import com.ainote.app.entity.Folder;
import com.ainote.app.entity.Note;
import com.ainote.app.entity.Schedule;
import com.ainote.app.repository.FolderRepository;
import com.ainote.app.repository.NoteConceptRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmartSuggestionServiceTest {

    private NoteRepository noteRepository;
    private NoteConceptRepository conceptRepository;
    private ScheduleRepository scheduleRepository;
    private FolderRepository folderRepository;
    private SmartSuggestionService service;

    @BeforeEach
    void setUp() {
        noteRepository = mock(NoteRepository.class);
        conceptRepository = mock(NoteConceptRepository.class);
        scheduleRepository = mock(ScheduleRepository.class);
        folderRepository = mock(FolderRepository.class);
        service = new SmartSuggestionService(noteRepository, conceptRepository, scheduleRepository, folderRepository);
    }

    @Test
    void generateSuggestions_withUserData_returnsTopThreeByPriority() {
        String userId = "user-1";
        Schedule overdue = schedule("schedule-1", "pay bill", LocalDateTime.now().minusHours(3));
        List<Note> notes = List.of(
                note("note-1", "old", LocalDateTime.now().minusDays(20)),
                note("note-2", "recent", LocalDateTime.now()),
                note("note-3", "recent", LocalDateTime.now()),
                note("note-4", "recent", LocalDateTime.now()),
                note("note-5", "recent", LocalDateTime.now())
        );

        when(scheduleRepository.findByUserIdOrderByStartTimeDesc(userId)).thenReturn(List.of(overdue));
        when(noteRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(notes);
        when(folderRepository.findByUserId(userId)).thenReturn(List.of(folder("folder-1")));

        List<SmartSuggestionService.Suggestion> suggestions = service.generateSuggestions(userId);

        assertThat(suggestions).hasSize(3);
        assertThat(suggestions).extracting(SmartSuggestionService.Suggestion::type)
                .containsExactly(
                        SmartSuggestionService.SuggestionType.OVERDUE_SCHEDULE,
                        SmartSuggestionService.SuggestionType.ORGANIZE_NOTES,
                        SmartSuggestionService.SuggestionType.REVIEW_NOTE
                );
        assertThat(suggestions.get(0).params()).containsEntry("scheduleId", "schedule-1");
        verify(scheduleRepository).findByUserIdOrderByStartTimeDesc(userId);
        verify(noteRepository).findByUserIdAndDeletedAtIsNull(userId);
        verify(folderRepository).findByUserId(userId);
    }

    @Test
    void generateSuggestions_withoutSignals_returnsEmptyList() {
        String userId = "user-1";
        when(scheduleRepository.findByUserIdOrderByStartTimeDesc(userId)).thenReturn(List.of());
        when(noteRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(List.of());
        when(folderRepository.findByUserId(userId)).thenReturn(List.of());

        List<SmartSuggestionService.Suggestion> suggestions = service.generateSuggestions(userId);

        assertThat(suggestions).isEmpty();
    }

    @Test
    void dismissSuggestion_suppressesThatSuggestionType() {
        String userId = "user-1";
        when(noteRepository.findByUserIdAndDeletedAtIsNull(userId)).thenReturn(List.of());
        when(folderRepository.findByUserId(userId)).thenReturn(List.of());

        service.dismissSuggestion(userId, "OVERDUE_SCHEDULE");
        List<SmartSuggestionService.Suggestion> suggestions = service.generateSuggestions(userId);

        assertThat(suggestions).isEmpty();
        verify(scheduleRepository, never()).findByUserIdOrderByStartTimeDesc(userId);
    }

    private static Schedule schedule(String id, String title, LocalDateTime startTime) {
        Schedule schedule = new Schedule();
        schedule.setId(id);
        schedule.setTitle(title);
        schedule.setStatus("pending");
        schedule.setStartTime(startTime);
        return schedule;
    }

    private static Note note(String id, String title, LocalDateTime updatedAt) {
        Note note = new Note();
        note.setId(id);
        note.setTitle(title);
        note.setContent("content");
        note.setCreatedAt(updatedAt.minusDays(1));
        note.setUpdatedAt(updatedAt);
        return note;
    }

    private static Folder folder(String id) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName("work");
        return folder;
    }
}
