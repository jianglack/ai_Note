package com.ainote.app.service;

import com.ainote.app.agent.AgentAssistant;
import com.ainote.app.agent.ConcurrencyGuard;
import com.ainote.app.agent.budget.TokenBudget;
import com.ainote.app.agent.guardrail.GuardrailResult;
import com.ainote.app.agent.guardrail.InputGuardrail;
import com.ainote.app.agent.guardrail.OutputGuardrail;
import com.ainote.app.agent.pending.PendingActionRegistry;
import com.ainote.app.agent.pipeline.ToolAuditLogger;
import com.ainote.app.agent.tools.ToolLoopDetector;
import com.ainote.app.config.MemoryProperties;
import com.ainote.app.entity.Note;
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

import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("AgentService delete-all notes pending action")
class AgentServiceDeleteAllPendingActionTest {

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
                mock(MemoryOrchestrator.class),
                new MemoryProperties(),
                mock(Tracer.class),
                mock(ToolLoopDetector.class),
                concurrencyGuard,
                mock(TokenBudget.class),
                inputGuardrail,
                mock(OutputGuardrail.class)
        );
    }

    @Test
    void shouldAcceptAiScopedDeleteAllNotesPendingAction() {
        String response = "PENDING_ACTION:{\"type\":\"DELETE_NOTES\",\"scope\":\"ALL_ACTIVE_NOTES\"}";

        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> actions =
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(agentService, "extractPendingActions", response);

        assertThat(actions).hasSize(1);
        assertThat(actions.get(0))
                .containsEntry("type", "DELETE_NOTES")
                .containsEntry("scope", "ALL_ACTIVE_NOTES");
    }

    @Test
    void shouldExecuteScopedDeleteAllNotesDirectlyAfterConfirmation() {
        Note first = new Note();
        first.setId("note-1");
        first.setTitle("First note");
        Note second = new Note();
        second.setId("note-2");
        second.setTitle("Second note");

        when(noteRepository.findByUserIdAndDeletedAtIsNull("user-1"))
                .thenReturn(List.of(first, second));

        AiChatResponse response = agentService.confirmAction(
                "user-1",
                "{\"type\":\"DELETE_NOTES\",\"scope\":\"ALL_ACTIVE_NOTES\"}",
                true,
                null
        );

        assertThat(response.getContent()).contains("2");
        verify(noteService).delete("note-1");
        verify(noteService).delete("note-2");
        verifyNoInteractions(agentAssistant);
    }

    @Test
    void shouldNotDeleteAnyNoteWhenDeleteAllNotesIsRejected() {
        AiChatResponse response = agentService.confirmAction(
                "user-1",
                "{\"type\":\"DELETE_NOTES\",\"scope\":\"ALL_ACTIVE_NOTES\",\"count\":\"2\"}",
                false,
                null
        );

        assertThat(response.getContent()).contains("2");
        verifyNoInteractions(noteService, agentAssistant);
    }
}
