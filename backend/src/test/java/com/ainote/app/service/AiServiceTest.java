package com.ainote.app.service;

import com.ainote.app.entity.UserMemory;
import com.ainote.app.model.ChatHistoryItem;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiService unit tests")
class AiServiceTest {

    @Mock
    private NoteService noteService;
    @Mock
    private FolderService folderService;
    @Mock
    private ChatModel chatModel;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private UserMemoryRepository userMemoryRepository;
    @Mock
    private PromptLoader promptLoader;

    private AiService aiService;

    @BeforeEach
    void setUp() {
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

    private static UserMemory memory(Long id, String type, String content, LocalDateTime createdAt) {
        UserMemory memory = new UserMemory();
        memory.setId(id);
        memory.setUserId("user-123");
        memory.setMessageType(type);
        memory.setContent(content);
        memory.setCreatedAt(createdAt);
        return memory;
    }

}
