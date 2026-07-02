package com.ainote.app.service;

import com.ainote.app.entity.UserMemory;
import com.ainote.app.model.ChatHistoryItem;
import com.ainote.app.model.ExtractedSchedule;
import com.ainote.app.model.Note;
import com.ainote.app.repository.UserMemoryRepository;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.util.PromptLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
@DisplayName("AiService unit tests")
class AiServiceTest {
    private NoteService noteService;
    private FolderService folderService;
    private ChatModel chatModel;
    private SecurityUtils securityUtils;
    private UserMemoryRepository userMemoryRepository;
    private PromptLoader promptLoader;
    private AiService aiService;

    @BeforeEach
    void setUp() {
        noteService = mock(NoteService.class);
        folderService = mock(FolderService.class);
        chatModel = mock(ChatModel.class);
        securityUtils = mock(SecurityUtils.class);
        userMemoryRepository = mock(UserMemoryRepository.class);
        promptLoader = mock(PromptLoader.class);
        aiService = new AiService(noteService, folderService, chatModel,
                securityUtils, userMemoryRepository, promptLoader, new ObjectMapper());
    }

    @Test
    void getChatHistoryReturnsChronologicalMessages() {
        UserMemory msg1 = memory(1L, "USER", "Hello", LocalDateTime.now());
        UserMemory msg2 = memory(2L, "AI", "Hi there!", LocalDateTime.now());
        when(userMemoryRepository.findByUserIdOrderByCreatedAtAsc(eq("user-123"), any(PageRequest.class)))
                .thenReturn(List.of(msg1, msg2));

        List<ChatHistoryItem> result = aiService.getChatHistory("user-123", 50);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getContent()).isEqualTo("Hello");
        assertThat(result.get(0).getRole()).isEqualTo("user");
        assertThat(result.get(1).getContent()).isEqualTo("Hi there!");
        assertThat(result.get(1).getRole()).isEqualTo("assistant");
    }

    @Test
    void extractSchedulesParsesJsonResponseFromModel() {
        when(noteService.getById("note-1")).thenReturn(Optional.of(note(
                "note-1",
                "Launch",
                "<p>Review on July 10, 2026 at 10:00 AM.</p>")));
        when(promptLoader.format(eq("extract-schedules.txt"), any(), any(), any()))
                .thenReturn("extract prompt");
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse("""
                {"schedules":[{"title":"Launch review","startTime":"2026-07-10T10:00:00","endTime":"2026-07-10T10:30:00","allDay":false,"confidence":0.92,"source":"Review on July 10"}]}
                """));

        ExtractedSchedule.ExtractResponse response = aiService.extractSchedules("note-1");

        assertThat(response.getSchedules()).hasSize(1);
        assertThat(response.getSchedules().get(0).getTitle()).isEqualTo("Launch review");
        assertThat(response.getSchedules().get(0).getStartTime()).isEqualTo("2026-07-10T10:00:00");
    }

    @Test
    void extractSchedulesFallsBackForExplicitEnglishDateWhenModelReturnsEmpty() {
        when(noteService.getById("note-1")).thenReturn(Optional.of(note(
                "note-1",
                "Launch",
                "<p>Schedule a launch readiness review on July 10, 2026 at 10:00 AM for 30 minutes with the release team.</p>")));
        when(promptLoader.format(eq("extract-schedules.txt"), any(), any(), any()))
                .thenReturn("extract prompt");
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse("{\"schedules\":[]}"));

        ExtractedSchedule.ExtractResponse response = aiService.extractSchedules("note-1");

        assertThat(response.getSchedules()).hasSize(1);
        ExtractedSchedule schedule = response.getSchedules().get(0);
        assertThat(schedule.getTitle()).isEqualTo("launch readiness review");
        assertThat(schedule.getStartTime()).isEqualTo("2026-07-10T10:00:00");
        assertThat(schedule.getEndTime()).isEqualTo("2026-07-10T10:30:00");
        assertThat(schedule.getSource()).contains("July 10, 2026");
    }

    private static UserMemory memory(Long id, String type, String content, LocalDateTime createdAt) {
        UserMemory memory = new UserMemory();
        memory.setId(id);
        memory.setUserId("user-123");
        memory.setMessageType(type);
        memory.setContent(content);
        memory.setCreatedAt(createdAt);
        return memory;
    }

    private static Note note(String id, String title, String content) {
        Note note = new Note();
        note.setId(id);
        note.setTitle(title);
        note.setContent(content);
        return note;
    }

    private static ChatResponse chatResponse(String text) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text.trim()))
                .build();
    }

}
