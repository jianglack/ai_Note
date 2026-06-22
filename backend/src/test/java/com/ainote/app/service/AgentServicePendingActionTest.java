package com.ainote.app.service;

import com.ainote.app.agent.AgentAssistant;
import com.ainote.app.agent.ConcurrencyGuard;
import com.ainote.app.agent.budget.TokenBudget;
import com.ainote.app.agent.guardrail.GuardrailResult;
import com.ainote.app.agent.guardrail.InputGuardrail;
import com.ainote.app.agent.guardrail.OutputGuardrail;
import com.ainote.app.agent.pending.PendingActionRegistry;
import com.ainote.app.entity.Note;
import com.ainote.app.agent.pipeline.ToolAuditLogger;
import com.ainote.app.agent.tools.ToolLoopDetector;
import com.ainote.app.memory.ReliableChatMemoryStore;
import com.ainote.app.model.AiChatResponse;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.UserMemoryRepository;
import com.ainote.app.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("AgentService PENDING_ACTION 解析")
class AgentServicePendingActionTest {

    private AgentService agentService;
    private AgentAssistant agentAssistant;
    private NoteRepository noteRepository;
    private NoteService noteService;
    private ConcurrencyGuard concurrencyGuard;
    private InputGuardrail inputGuardrail;

    @BeforeEach
    void setUp() {
        agentAssistant = mock(AgentAssistant.class);
        noteRepository = mock(NoteRepository.class);
        noteService = mock(NoteService.class);
        concurrencyGuard = mock(ConcurrencyGuard.class);
        inputGuardrail = mock(InputGuardrail.class);
        ObjectMapper objectMapper = new ObjectMapper();

        agentService = new AgentService(
                agentAssistant,
                noteRepository,
                noteService,
                mock(SecurityUtils.class),
                mock(UserMemoryRepository.class),
                objectMapper,
                new PendingActionRegistry(objectMapper),
                mock(ExecutorService.class),
                mock(ContextAssembler.class),
                mock(ToolCallAuditor.class),
                mock(ReliableChatMemoryStore.class),
                mock(MemoryExtractionService.class),
                mock(Tracer.class),
                mock(ToolLoopDetector.class),
                concurrencyGuard,
                mock(TokenBudget.class),
                inputGuardrail,
                mock(OutputGuardrail.class)
        );
    }

    @Test
    @DisplayName("PENDING_ACTION 后面带确认文字时只解析 JSON 部分")
    @SuppressWarnings("unchecked")
    void shouldExtractJsonOnlyWhenPendingActionHasText() {
        String response = "PENDING_ACTION:{\"type\":\"DELETE_NOTE\",\"noteId\":\"note-1\",\"title\":\"测试笔记\"}\n确认删除吗？";

        List<Map<String, Object>> actions =
                ReflectionTestUtils.invokeMethod(agentService, "extractPendingActions", response);

        assertThat(actions).hasSize(1);
        assertThat(actions.get(0))
                .containsEntry("type", "DELETE_NOTE")
                .containsEntry("noteId", "note-1")
                .containsEntry("title", "测试笔记");
    }

    @Test
    @DisplayName("PENDING_ACTION 标题包含花括号时仍应按完整 JSON 边界解析")
    @SuppressWarnings("unchecked")
    void shouldExtractPendingActionWhenStringValueContainsBraces() {
        String response = "PENDING_ACTION:{\"type\":\"DELETE_NOTE\",\"noteId\":\"note-1\",\"title\":\"研究 {draft} 记录\"}\n确认删除吗？";

        List<Map<String, Object>> actions =
                ReflectionTestUtils.invokeMethod(agentService, "extractPendingActions", response);
        String cleaned = ReflectionTestUtils.invokeMethod(agentService, "removePendingActionMarkers", response);

        assertThat(actions).hasSize(1);
        assertThat(actions.get(0))
                .containsEntry("type", "DELETE_NOTE")
                .containsEntry("noteId", "note-1")
                .containsEntry("title", "研究 {draft} 记录");
        assertThat(cleaned).isEqualTo("确认删除吗？");
    }

    @Test
    @DisplayName("未知 PENDING_ACTION 类型不应进入确认链路")
    @SuppressWarnings("unchecked")
    void shouldRejectUnknownPendingActionType() {
        String response = "PENDING_ACTION:{\"type\":\"FORMAT_DISK\",\"noteId\":\"note-1\"}\n确认执行吗？";

        List<Map<String, Object>> actions =
                ReflectionTestUtils.invokeMethod(agentService, "extractPendingActions", response);

        assertThat(actions).isEmpty();
    }

    @Test
    @DisplayName("缺少必需字段的 PENDING_ACTION 不应进入确认链路")
    @SuppressWarnings("unchecked")
    void shouldRejectPendingActionMissingRequiredField() {
        String response = "PENDING_ACTION:{\"type\":\"DELETE_NOTE\",\"title\":\"缺少 ID\"}\n确认删除吗？";

        List<Map<String, Object>> actions =
                ReflectionTestUtils.invokeMethod(agentService, "extractPendingActions", response);

        assertThat(actions).isEmpty();
    }

    @Test
    @DisplayName("最终回复没有标记时，应从工具审计结果中提取 PENDING_ACTION")
    @SuppressWarnings("unchecked")
    void shouldCollectPendingActionFromToolTranscript() {
        String finalResponse = "需要你确认一下：确认删除「测试笔记」吗？";
        List<ToolAuditLogger.ToolAuditEntry> transcript = List.of(
                new ToolAuditLogger.ToolAuditEntry(
                        "req-1",
                        "noteAction",
                        "delete",
                        "{\"noteId\":\"note-1\"}",
                        "PENDING_CONFIRM",
                        "PENDING_ACTION:{\"type\":\"DELETE_NOTE\",\"noteId\":\"note-1\",\"title\":\"测试笔记\"}\n确认删除吗？",
                        12,
                        false,
                        null,
                        null
                )
        );

        List<Map<String, Object>> actions =
                ReflectionTestUtils.invokeMethod(agentService, "collectPendingActions", finalResponse, transcript);

        assertThat(actions).hasSize(1);
        assertThat(actions.get(0))
                .containsEntry("type", "DELETE_NOTE")
                .containsEntry("noteId", "note-1")
                .containsEntry("title", "测试笔记");
    }

    @Test
    @DisplayName("删除当前选中笔记时，后端直接生成确认卡片数据")
    void shouldBuildPendingActionDirectlyForSelectedNoteDelete() {
        Note selected = new Note();
        selected.setId("selected-note");
        selected.setTitle("测试-上下文工程与记忆系统记录");
        selected.setContent("content");

        when(inputGuardrail.check("删除笔记")).thenReturn(GuardrailResult.ok());
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("selected-note", "user-1"))
                .thenReturn(Optional.of(selected));

        AiChatResponse response = agentService.chat("删除笔记", List.of("selected-note"), "user-1");

        assertThat(response.getContent()).contains("确认删除「测试-上下文工程与记忆系统记录」");
        assertThat(response.getAction()).contains("\"type\":\"DELETE_NOTE\"");
        assertThat(response.getAction()).contains("\"noteId\":\"selected-note\"");
        assertThat(response.getAction()).contains("\"title\":\"测试-上下文工程与记忆系统记录\"");
        verifyNoInteractions(agentAssistant);
        verify(concurrencyGuard, never()).tryAcquire(anyString(), anyLong());
    }

    @Test
    @DisplayName("复杂多步请求不应被当前笔记删除的确定性分支拦截")
    void shouldNotInterceptComplexSelectedNoteDeleteRequest() {
        Boolean simpleDelete = ReflectionTestUtils.invokeMethod(
                agentService,
                "isCurrentSelectedNoteDeleteRequest",
                "删除这篇笔记"
        );
        Boolean complexDelete = ReflectionTestUtils.invokeMethod(
                agentService,
                "isCurrentSelectedNoteDeleteRequest",
                "删除这篇笔记，然后创建一个日程"
        );

        assertThat(simpleDelete).isTrue();
        assertThat(complexDelete).isFalse();
    }

    @Test
    @DisplayName("确认 DELETE_NOTE 后由后端直接执行删除，不再交给 LLM")
    void shouldExecuteDeleteNoteDirectlyAfterConfirmation() {
        com.ainote.app.model.Note note = new com.ainote.app.model.Note();
        note.setId("selected-note");
        note.setTitle("测试-上下文工程与记忆系统记录");

        when(noteService.getById("selected-note")).thenReturn(Optional.of(note));

        AiChatResponse response = agentService.confirmAction(
                "user-1",
                "{\"type\":\"DELETE_NOTE\",\"noteId\":\"selected-note\",\"title\":\"测试-上下文工程与记忆系统记录\"}",
                true,
                null
        );

        assertThat(response.getContent()).contains("已删除「测试-上下文工程与记忆系统记录」");
        verify(noteService).delete("selected-note");
        verifyNoInteractions(agentAssistant);
    }
}
