package com.ainote.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainote.app.agent.CancellationToken;
import com.ainote.app.config.GlobalExceptionHandler;
import com.ainote.app.entity.Note;
import com.ainote.app.model.AiChatResponse;
import com.ainote.app.model.ClassificationResponse;
import com.ainote.app.model.ClassificationSuggestion;
import com.ainote.app.model.ExtractedSchedule;
import com.ainote.app.repository.AgentTraceRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.AgentService;
import com.ainote.app.service.AiService;
import com.ainote.app.service.LangChain4jRagService;
import com.ainote.app.service.RagFeedbackService;
import com.ainote.app.service.SmartSuggestionService;
import com.ainote.app.service.chat.ChatOrchestrator;
import com.ainote.app.service.chat.StreamCallback;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AiControllerEndpointSupplementTest {

    private AiService aiService;
    private SecurityUtils securityUtils;
    private AgentTraceRepository traceRepository;
    private RagFeedbackService ragFeedbackService;
    private SmartSuggestionService smartSuggestionService;
    private NoteRepository noteRepository;
    private AgentService agentService;
    private LangChain4jRagService ragService;
    private ExecutorService executorService;
    private ChatOrchestrator chatOrchestrator;
    private ObjectMapper objectMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        aiService = mock(AiService.class);
        securityUtils = mock(SecurityUtils.class);
        traceRepository = mock(AgentTraceRepository.class);
        ragFeedbackService = mock(RagFeedbackService.class);
        smartSuggestionService = mock(SmartSuggestionService.class);
        noteRepository = mock(NoteRepository.class);
        agentService = mock(AgentService.class);
        ragService = mock(LangChain4jRagService.class);
        executorService = mock(ExecutorService.class);
        chatOrchestrator = mock(ChatOrchestrator.class);
        objectMapper = new ObjectMapper();

        AiController controller = new AiController(
                aiService,
                securityUtils,
                traceRepository,
                objectMapper,
                executorService,
                ragFeedbackService,
                smartSuggestionService,
                noteRepository,
                agentService,
                ragService,
                chatOrchestrator);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        when(securityUtils.getCurrentUserId()).thenReturn("user-1");
    }

    @Test
    void chatStreamEmitsTokenProgressAndCompleteEvents() throws Exception {
        AtomicReference<Runnable> streamWork = new AtomicReference<>();
        doAnswer(invocation -> {
            streamWork.set(invocation.getArgument(0));
            return null;
        }).when(executorService).execute(any(Runnable.class));
        when(agentService.createCancelToken(eq("user-1"), anyString()))
                .thenReturn(new CancellationToken());
        doAnswer(invocation -> {
            StreamCallback callback = invocation.getArgument(4, StreamCallback.class);
            callback.onToken("hello");
            callback.onProgress("retrieve", "checking");
            callback.onComplete(new AiChatResponse("done", Map.of()));
            return null;
        }).when(chatOrchestrator).chatStream(
                eq("hello"),
                eq(List.of("n1")),
                eq("user-1"),
                anyString(),
                any(StreamCallback.class));

        MvcResult result = mockMvc.perform(post("/api/ai/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("query", "hello", "noteIds", List.of("n1")))))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(streamWork.get()).isNotNull();
        streamWork.get().run();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("retry:3000")))
                .andExpect(content().string(containsString("id:")))
                .andExpect(content().string(containsString("event:heartbeat")))
                .andExpect(content().string(containsString("event:token")))
                .andExpect(content().string(containsString("data:hello")))
                .andExpect(content().string(containsString("event:progress")))
                .andExpect(content().string(containsString("retrieve")))
                .andExpect(content().string(containsString("event:complete")))
                .andExpect(content().string(containsString("\"content\":\"done\"")));

        verify(chatOrchestrator).chatStream(
                eq("hello"),
                eq(List.of("n1")),
                eq("user-1"),
                anyString(),
                any(StreamCallback.class));
    }

    @Test
    void chatStreamEventSourceGetAcceptsQueryAndRepeatedNoteIds() throws Exception {
        AtomicReference<Runnable> streamWork = new AtomicReference<>();
        doAnswer(invocation -> {
            streamWork.set(invocation.getArgument(0));
            return null;
        }).when(executorService).execute(any(Runnable.class));
        when(agentService.createCancelToken(eq("user-1"), anyString()))
                .thenReturn(new CancellationToken());
        doAnswer(invocation -> {
            StreamCallback callback = invocation.getArgument(4, StreamCallback.class);
            callback.onToken("hello");
            callback.onComplete(new AiChatResponse("done", Map.of()));
            return null;
        }).when(chatOrchestrator).chatStream(
                eq("hello"),
                eq(List.of("n1", "n2")),
                eq("user-1"),
                anyString(),
                any(StreamCallback.class));

        MvcResult result = mockMvc.perform(get("/api/ai/chat/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .param("query", "hello")
                        .param("noteIds", "n1", "n2"))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(streamWork.get()).isNotNull();
        streamWork.get().run();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("retry:3000")))
                .andExpect(content().string(containsString("id:")))
                .andExpect(content().string(containsString("event:heartbeat")))
                .andExpect(content().string(containsString("event:token")))
                .andExpect(content().string(containsString("event:complete")));

        verify(chatOrchestrator).chatStream(
                eq("hello"),
                eq(List.of("n1", "n2")),
                eq("user-1"),
                anyString(),
                any(StreamCallback.class));
    }

    @Test
    void chatStreamEmitsErrorEventWhenOrchestratorReportsError() throws Exception {
        AtomicReference<Runnable> streamWork = new AtomicReference<>();
        doAnswer(invocation -> {
            streamWork.set(invocation.getArgument(0));
            return null;
        }).when(executorService).execute(any(Runnable.class));
        when(agentService.createCancelToken(eq("user-1"), anyString()))
                .thenReturn(new CancellationToken());
        doAnswer(invocation -> {
            StreamCallback callback = invocation.getArgument(4, StreamCallback.class);
            callback.onError("model unavailable");
            return null;
        }).when(chatOrchestrator).chatStream(
                eq("hello"),
                eq(List.of()),
                eq("user-1"),
                anyString(),
                any(StreamCallback.class));

        MvcResult result = mockMvc.perform(post("/api/ai/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("query", "hello"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(streamWork.get()).isNotNull();
        streamWork.get().run();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:error")))
                .andExpect(content().string(containsString("data:model unavailable")));
    }

    @Test
    void chatSaveRejectsEmptyBodyAndPersistsValidMessages() throws Exception {
        mockMvc.perform(post("/api/ai/chat/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("参数校验")));

        mockMvc.perform(post("/api/ai/chat/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userMessage", "hello", "aiReply", "hi"))))
                .andExpect(status().isOk());

        verify(aiService).saveChatTurn("user-1", "hello", "hi");
    }

    @Test
    void spiritGreetingReturnsNonEmptyGreeting() throws Exception {
        when(aiService.spiritGreeting()).thenReturn("hello");

        mockMvc.perform(get("/api/ai/spirit/greeting"))
                .andExpect(status().isOk())
                .andExpect(content().string("hello"));
    }

    @Test
    void suggestionsReturnGeneratedCardsAndFallbackToEmptyList() throws Exception {
        when(smartSuggestionService.generateSuggestions("user-1"))
                .thenReturn(List.of(new SmartSuggestionService.Suggestion(
                        SmartSuggestionService.SuggestionType.REVIEW_NOTE,
                        "复习笔记「Spring」",
                        Map.of("noteId", "n1", "title", "Spring"),
                        40)));

        mockMvc.perform(get("/api/ai/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("REVIEW_NOTE"))
                .andExpect(jsonPath("$[0].card.kind").value("suggestion"));

        when(smartSuggestionService.generateSuggestions("user-1"))
                .thenThrow(new IllegalStateException("store down"));

        mockMvc.perform(get("/api/ai/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void extractSchedulesValidatesNoteIdAndReturnsMultipleSchedules() throws Exception {
        when(aiService.extractSchedules("n1"))
                .thenReturn(new ExtractedSchedule.ExtractResponse(List.of(
                        new ExtractedSchedule("开会", "2026-06-27T09:00:00", null, false, null, 0.9, "line1"),
                        new ExtractedSchedule("复盘", "2026-06-28T10:00:00", null, false, null, 0.8, "line2")
                ), "n1"));

        mockMvc.perform(post("/api/ai/extract-schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("noteId", "n1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteId").value("n1"))
                .andExpect(jsonPath("$.schedules.length()").value(2))
                .andExpect(jsonPath("$.schedules[0].title").value("开会"));

        mockMvc.perform(post("/api/ai/extract-schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void traceStatsAggregateForCurrentUserOnly() throws Exception {
        when(traceRepository.countByUserId("user-1")).thenReturn(12L);
        when(traceRepository.sumTotalTokensByUserId("user-1")).thenReturn(345L);
        when(traceRepository.countByUserIdAndCreatedAtBetween(eq("user-1"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(2L);
        when(traceRepository.sumTotalTokensByUserIdAndDateRange(eq("user-1"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(67L);

        mockMvc.perform(get("/api/ai/traces/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCalls").value(12))
                .andExpect(jsonPath("$.totalTokens").value(345))
                .andExpect(jsonPath("$.todayCalls").value(2))
                .andExpect(jsonPath("$.todayTokens").value(67));
    }

    @Test
    void suggestTagsValidatesRequestAndReturnsServiceResponse() throws Exception {
        when(aiService.spiritSuggestTags("n1")).thenReturn("java, spring");

        mockMvc.perform(post("/api/ai/spirit/suggest-tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("noteId", "n1"))))
                .andExpect(status().isOk())
                .andExpect(content().string("java, spring"));

        mockMvc.perform(post("/api/ai/spirit/suggest-tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void classifyReturnsClassificationResponse() throws Exception {
        when(aiService.classifyNotes())
                .thenReturn(new ClassificationResponse(
                        List.of(new ClassificationSuggestion("n1", "Spring", "f1", "技术", "主题匹配", false)),
                        List.of("新文件夹"),
                        "已完成分类"));

        mockMvc.perform(post("/api/ai/classify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].noteId").value("n1"))
                .andExpect(jsonPath("$.newFolders[0]").value("新文件夹"))
                .andExpect(jsonPath("$.summary").value(containsString("完成分类")));
    }

    @Test
    void actionFeedbackConfirmsAndRejectsPendingActions() throws Exception {
        when(agentService.confirmAction(eq("user-1"), anyString(), eq(true), eq("ok")))
                .thenReturn(new AiChatResponse("已执行", Map.of()));
        when(agentService.confirmAction(eq("user-1"), anyString(), eq(false), eq("no")))
                .thenReturn(new AiChatResponse("已取消", Map.of()));

        mockMvc.perform(post("/api/ai/action-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("actionJson", "{\"type\":\"DELETE_NOTE\"}", "confirmed", true, "feedback", "ok"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(containsString("已执行")));

        mockMvc.perform(post("/api/ai/action-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("actionJson", "{\"type\":\"DELETE_NOTE\"}", "confirmed", false, "feedback", "no"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(containsString("已取消")));

        mockMvc.perform(post("/api/ai/action-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("actionJson", "{\"type\":\"DELETE_NOTE\"}"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ragFeedbackValidatesAndRecordsDefaults() throws Exception {
        mockMvc.perform(post("/api/ai/rag-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("query", "vector search"))))
                .andExpect(status().isOk());

        verify(ragFeedbackService).recordFeedback("user-1", "vector search", null, 0.0, "CLICK");

        mockMvc.perform(post("/api/ai/rag-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("query", "q", "similarityScore", 1.5))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generateCanvasReturnsEmptyGraphAndSemanticGraph() throws Exception {
        when(noteRepository.findByUserIdAndDeletedAtIsNull("user-1")).thenReturn(List.of());

        mockMvc.perform(post("/api/ai/generate-canvas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes.length()").value(0))
                .andExpect(jsonPath("$.edges.length()").value(0));

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(ragService.getEmbeddingModel()).thenReturn(embeddingModel);
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(Embedding.from(new float[] {1.0f, 0.0f})));
        when(noteRepository.findByUserIdAndDeletedAtIsNull("user-1"))
                .thenReturn(List.of(note("n1", "Spring", "Spring Boot notes"),
                        note("n2", "Java", "Spring Boot patterns")));

        mockMvc.perform(post("/api/ai/generate-canvas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("limit", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes.length()").value(2))
                .andExpect(jsonPath("$.nodes[0].noteId").value("n1"))
                .andExpect(jsonPath("$.edges.length()").value(1))
                .andExpect(jsonPath("$.edges[0].from").value("n1"))
                .andExpect(jsonPath("$.edges[0].to").value("n2"));

        mockMvc.perform(post("/api/ai/generate-canvas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("limit", 101))))
                .andExpect(status().isBadRequest());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static Note note(String id, String title, String content) {
        Note note = new Note();
        note.setId(id);
        note.setTitle(title);
        note.setContent(content);
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());
        return note;
    }
}
