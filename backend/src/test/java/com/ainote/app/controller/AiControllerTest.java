package com.ainote.app.controller;

import com.ainote.app.entity.AgentTrace;
import com.ainote.app.repository.AgentTraceRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.AgentService;
import com.ainote.app.service.AiService;
import com.ainote.app.service.LangChain4jRagService;
import com.ainote.app.service.RagFeedbackService;
import com.ainote.app.service.SmartSuggestionService;
import com.ainote.app.service.chat.ChatOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiController 单元测试")
class AiControllerTest {

    @Mock private AiService aiService;
    @Mock private SecurityUtils securityUtils;
    @Mock private AgentTraceRepository traceRepository;
    @Mock private ExecutorService executorService;
    @Mock private RagFeedbackService ragFeedbackService;
    @Mock private SmartSuggestionService smartSuggestionService;
    @Mock private NoteRepository noteRepository;
    @Mock private AgentService agentService;
    @Mock private LangChain4jRagService ragService;
    @Mock private ChatOrchestrator chatOrchestrator;

    @Test
    @DisplayName("getTraces 应支持时间范围和模型筛选")
    void shouldFilterTracesByDateRangeAndModel() {
        AiController controller = new AiController(
            aiService, securityUtils, traceRepository, new ObjectMapper(), executorService,
            ragFeedbackService, smartSuggestionService, noteRepository, agentService, ragService,
            chatOrchestrator
        );
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 4, 23, 59);
        AgentTrace trace = new AgentTrace();

        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(traceRepository.findFilteredTraces(
                org.mockito.Mockito.eq("user-123"),
                org.mockito.Mockito.eq(start),
                org.mockito.Mockito.eq(end),
                org.mockito.Mockito.eq("deepseek-chat"),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
            .thenReturn(List.of(trace));

        var response = controller.getTraces(25, start, end, "deepseek-chat");

        assertThat(response.getBody()).containsExactly(trace);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(traceRepository).findFilteredTraces(
            org.mockito.Mockito.eq("user-123"),
            org.mockito.Mockito.eq(start),
            org.mockito.Mockito.eq(end),
            org.mockito.Mockito.eq("deepseek-chat"),
            pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(25);
    }

    @Test
    @DisplayName("generateCanvas \u5f02\u5e38\u65f6\u4e0d\u6cc4\u9732\u5185\u90e8\u9519\u8bef\u4fe1\u606f")
    void generateCanvas_shouldNotLeakExceptionMessage() {
        AiController controller = new AiController(
            aiService, securityUtils, traceRepository, new ObjectMapper(), executorService,
            ragFeedbackService, smartSuggestionService, noteRepository, agentService, ragService,
            chatOrchestrator
        );

        when(securityUtils.getCurrentUserId()).thenReturn("user-123");
        when(noteRepository.findByUserIdAndDeletedAtIsNull("user-123"))
                .thenThrow(new RuntimeException("secret internal path /tmp/foo"));

        var response = controller.generateCanvas(null);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        Map<String, Object> body = response.getBody();
        assertThat(body.get("error")).isEqualTo("\u5d4c\u5165\u53ef\u89c6\u5316\u751f\u6210\u5931\u8d25");
        assertThat(body.get("error").toString()).doesNotContain("secret internal path");
    }
}
