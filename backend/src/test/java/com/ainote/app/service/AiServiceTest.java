package com.ainote.app.service;

import com.ainote.app.entity.ChatMemoryHead;
import com.ainote.app.entity.UserMemory;
import com.ainote.app.memory.ReliableChatMemoryStore;
import com.ainote.app.memory.ShortTermMemoryMetrics;
import com.ainote.app.model.ChatHistoryItem;
import com.ainote.app.model.ChatHistoryPage;
import com.ainote.app.model.ExtractedSchedule;
import com.ainote.app.model.Note;
import com.ainote.app.repository.ChatMemoryHeadRepository;
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
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
@DisplayName("AiService unit tests")
class AiServiceTest {
    private NoteService noteService;
    private FolderService folderService;
    private ChatModel chatModel;
    private SecurityUtils securityUtils;
    private UserMemoryRepository userMemoryRepository;
    private ChatMemoryHeadRepository chatMemoryHeadRepository;
    private ReliableChatMemoryStore reliableChatMemoryStore;
    private ShortTermMemoryMetrics shortTermMemoryMetrics;
    private PromptLoader promptLoader;
    private AiService aiService;

    @BeforeEach
    void setUp() {
        noteService = mock(NoteService.class);
        folderService = mock(FolderService.class);
        chatModel = mock(ChatModel.class);
        securityUtils = mock(SecurityUtils.class);
        userMemoryRepository = mock(UserMemoryRepository.class);
        chatMemoryHeadRepository = mock(ChatMemoryHeadRepository.class);
        reliableChatMemoryStore = mock(ReliableChatMemoryStore.class);
        shortTermMemoryMetrics = mock(ShortTermMemoryMetrics.class);
        promptLoader = mock(PromptLoader.class);
        aiService = new AiService(noteService, folderService, chatModel,
                securityUtils, userMemoryRepository, chatMemoryHeadRepository,
                reliableChatMemoryStore, shortTermMemoryMetrics, promptLoader, new ObjectMapper());
    }

    @Test
    void getChatHistoryReturnsChronologicalMessages() {
        LocalDateTime base = LocalDateTime.of(2026, 7, 3, 10, 0);
        UserMemory msg1 = memory(1L, "USER", "Hello", base);
        UserMemory msg2 = memory(2L, "AI", "Hi there!", base.plusSeconds(1));
        when(userMemoryRepository.findByUserIdOrderByCreatedAtDesc(eq("user-123"), any(PageRequest.class)))
                .thenReturn(List.of(msg2, msg1));

        List<ChatHistoryItem> result = aiService.getChatHistory("user-123", 50);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getContent()).isEqualTo("Hello");
        assertThat(result.get(0).getRole()).isEqualTo("user");
        assertThat(result.get(1).getContent()).isEqualTo("Hi there!");
        assertThat(result.get(1).getRole()).isEqualTo("assistant");
    }

    @Test
    void getChatHistoryReturnsLatestMessagesInsteadOfOldestPage() {
        LocalDateTime base = LocalDateTime.of(2026, 7, 3, 10, 0);
        UserMemory oldUser = memory(1L, "USER", "old user", base);
        UserMemory oldAi = memory(2L, "AI", "old ai", base.plusSeconds(1));
        UserMemory latestUser = memory(3L, "USER", "latest user", base.plusSeconds(2));
        UserMemory latestAi = memory(4L, "AI", "latest ai", base.plusSeconds(3));

        when(userMemoryRepository.findByUserIdOrderByCreatedAtAsc(eq("user-123"), any(PageRequest.class)))
                .thenReturn(List.of(oldUser, oldAi));
        when(userMemoryRepository.findByUserIdOrderByCreatedAtDesc(eq("user-123"), any(PageRequest.class)))
                .thenReturn(List.of(latestAi, latestUser));

        List<ChatHistoryItem> result = aiService.getChatHistory("user-123", 2);

        assertThat(result).extracting(ChatHistoryItem::getContent)
                .containsExactly("latest user", "latest ai");
    }

    @Test
    void getChatHistoryPageReturnsLatestMessagesInChronologicalOrderAndHasMore() {
        LocalDateTime base = LocalDateTime.of(2026, 7, 3, 10, 0);
        UserMemory msg1 = memory(1L, "USER", "oldest hidden", base);
        UserMemory msg2 = memory(2L, "AI", "visible user", base.plusSeconds(1));
        UserMemory msg3 = memory(3L, "USER", "visible ai", base.plusSeconds(2));

        when(userMemoryRepository.findVisibleByUserIdBeforeIdOrderByCreatedAtDesc(
                eq("user-123"), org.mockito.Mockito.isNull(), any(PageRequest.class)))
                .thenReturn(List.of(msg3, msg2, msg1));

        ChatHistoryPage page = aiService.getChatHistoryPage("user-123", 2, null);

        assertThat(page.getItems()).extracting(ChatHistoryItem::getContent)
                .containsExactly("visible user", "visible ai");
        assertThat(page.isHasMore()).isTrue();
        assertThat(page.getNextCursor()).isEqualTo("2");
    }

    @Test
    void getChatHistoryPageUsesBeforeCursorForOlderMessages() {
        LocalDateTime base = LocalDateTime.of(2026, 7, 3, 10, 0);
        UserMemory older = memory(1L, "USER", "older", base);
        UserMemory oldest = memory(2L, "AI", "oldest", base.plusSeconds(1));

        when(userMemoryRepository.findVisibleByUserIdBeforeIdOrderByCreatedAtDesc(
                eq("user-123"), eq(20L), any(PageRequest.class)))
                .thenReturn(List.of(oldest, older));

        ChatHistoryPage page = aiService.getChatHistoryPage("user-123", 100, "20");

        assertThat(page.getItems()).extracting(ChatHistoryItem::getContent)
                .containsExactly("older", "oldest");
        assertThat(page.isHasMore()).isFalse();
        assertThat(page.getNextCursor()).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void saveChatTurnAllocatesConsecutiveSequencesInOneBatch() {
        ChatMemoryHead head = new ChatMemoryHead();
        head.setUserId("user-123");
        head.setNextSequenceNumber(7);
        when(chatMemoryHeadRepository.findByUserIdForUpdate("user-123")).thenReturn(Optional.of(head));

        aiService.saveChatTurn("user-123", "question", "answer");

        ArgumentCaptor<List<UserMemory>> rows = ArgumentCaptor.forClass(List.class);
        verify(userMemoryRepository).saveAll(rows.capture());
        assertThat(rows.getValue()).extracting(UserMemory::getSequenceNumber)
                .containsExactly(7, 8);
        assertThat(rows.getValue()).extracting(UserMemory::getMessageType)
                .containsExactly("USER", "AI");
        assertThat(head.getNextSequenceNumber()).isEqualTo(9);
        verify(chatMemoryHeadRepository).save(head);
        verify(shortTermMemoryMetrics).recordAppend(2);
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
