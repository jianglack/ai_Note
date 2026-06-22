package com.ainote.app.service;

import com.ainote.app.repository.NoteConceptRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.ScheduleRepository;
import com.ainote.app.util.PromptLoader;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteInsightServiceTest {

    @Mock private NoteRepository noteRepository;
    @Mock private NoteConceptRepository conceptRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private ChatModel chatModel;
    @Mock private PromptLoader promptLoader;

    private NoteInsightService service;

    @BeforeEach
    void setUp() {
        service = new NoteInsightService(
                noteRepository,
                conceptRepository,
                scheduleRepository,
                chatModel,
                promptLoader);
    }

    @Test
    void getStatistics_usesAggregateQueriesInsteadOfLoadingAllNotes() {
        when(noteRepository.countByUserIdAndDeletedAtIsNull("user-1")).thenReturn(7L);
        when(conceptRepository.countDistinctConceptsByUserId("user-1")).thenReturn(3L);
        when(noteRepository.countNotesByFolder("user-1"))
                .thenReturn(List.of(folderCount("work", 4L), folderCount("Uncategorized", 3L)));
        when(noteRepository.countRecentlyActiveByUserId(eq("user-1"), any(LocalDateTime.class)))
                .thenReturn(2L);
        when(noteRepository.findRecentTitlesByUserId(eq("user-1"), any(Pageable.class)))
                .thenReturn(List.of("recent-a", "recent-b"));
        when(conceptRepository.findTopConceptsByUserId(eq("user-1"), any(Pageable.class)))
                .thenReturn(List.of("ai", "java"));

        Map<String, Object> stats = service.getStatistics("user-1");

        assertThat(stats.get("totalNotes")).isEqualTo(7L);
        assertThat(stats.get("totalConcepts")).isEqualTo(3L);
        assertThat(stats.get("notesByFolder")).isEqualTo(Map.of("work", 4L, "Uncategorized", 3L));
        assertThat(stats.get("recentlyActive")).isEqualTo(2L);
        assertThat(stats.get("recentNotes")).isEqualTo(List.of("recent-a", "recent-b"));
        assertThat(stats.get("topConcepts")).isEqualTo(List.of("ai", "java"));

        verify(noteRepository, never()).findByUserIdAndDeletedAtIsNull("user-1");
        verify(conceptRepository, never()).findByUserId("user-1");
    }

    private static NoteRepository.FolderCount folderCount(String folderName, long noteCount) {
        return new NoteRepository.FolderCount() {
            @Override
            public String getFolderName() {
                return folderName;
            }

            @Override
            public Long getNoteCount() {
                return noteCount;
            }
        };
    }
}
