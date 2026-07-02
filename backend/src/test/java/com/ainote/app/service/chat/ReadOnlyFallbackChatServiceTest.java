package com.ainote.app.service.chat;

import com.ainote.app.agent.guardrail.OutputGuardrail;
import com.ainote.app.entity.Note;
import com.ainote.app.model.AiChatResponse;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.service.ContextAssembler;
import com.ainote.app.util.PromptLoader;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class ReadOnlyFallbackChatServiceTest {

    private ChatModel chatModel;
    private ContextAssembler contextAssembler;
    private NoteRepository noteRepository;
    private OutputGuardrail outputGuardrail;
    private PromptLoader promptLoader;
    private ChatMetrics chatMetrics;
    private ReadOnlyFallbackChatService service;

    @BeforeEach
    void setUp() {
        chatModel = Mockito.mock(ChatModel.class);
        contextAssembler = Mockito.mock(ContextAssembler.class);
        noteRepository = Mockito.mock(NoteRepository.class);
        outputGuardrail = Mockito.mock(OutputGuardrail.class);
        promptLoader = Mockito.mock(PromptLoader.class);
        chatMetrics = Mockito.mock(ChatMetrics.class);

        when(promptLoader.load("fallback-system.txt")).thenReturn("你是助手。");
        when(outputGuardrail.sanitize(anyString(), anyString())).thenAnswer(i -> i.getArgument(0));

        service = new ReadOnlyFallbackChatService(
                chatModel, contextAssembler, noteRepository,
                outputGuardrail, promptLoader, chatMetrics);
    }

    @Test
    void level1_usesFullContext() {
        when(contextAssembler.assemble("问题", List.of("n1"), "u1"))
                .thenReturn("完整上下文内容");
        when(chatModel.chat(anyString())).thenReturn("AI 回复");

        AiChatResponse response = service.chat("问题", List.of("n1"), "u1");

        assertThat(response.isDegraded()).isTrue();
        assertThat(response.getChatMode()).isEqualTo("FALLBACK");
        assertThat(response.getContent()).contains("回复");
        assertThat(response.getDegradationReason()).contains("完整上下文");
    }

    @Test
    void level2_fallsBackToNotesWhenContextAssemblerFails() {
        when(contextAssembler.assemble(anyString(), anyList(), anyString()))
                .thenThrow(new RuntimeException("DB down"));

        Note note = new Note();
        note.setTitle("测试笔记");
        note.setContent("笔记内容");
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull("n1", "u1"))
                .thenReturn(Optional.of(note));
        when(chatModel.chat(anyString())).thenReturn("基于笔记回复");

        AiChatResponse response = service.chat("问题", List.of("n1"), "u1");

        assertThat(response.getDegradationReason()).contains("笔记内容");
    }

    @Test
    void level3_fallsBackToQueryOnlyWhenNotesFail() {
        when(contextAssembler.assemble(anyString(), anyList(), anyString()))
                .thenThrow(new RuntimeException("DB down"));
        when(noteRepository.findByIdAndUserIdAndDeletedAtIsNull(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(chatModel.chat(anyString())).thenReturn("基础回复");

        AiChatResponse response = service.chat("问题", List.of("n1"), "u1");

        assertThat(response.getDegradationReason()).contains("基础回复");
        assertThat(response.getContent()).contains("回复");
    }

    @Test
    void outputGuardrailFailureReturnsSafeMessage() {
        when(contextAssembler.assemble(anyString(), anyList(), anyString()))
                .thenReturn("context");
        when(chatModel.chat(anyString())).thenReturn("raw response");
        when(outputGuardrail.sanitize(anyString(), anyString()))
                .thenThrow(new RuntimeException("guardrail crash"));

        AiChatResponse response = service.chat("q", List.of(), "u");

        assertThat(response.getContent()).contains("抱歉");
    }

    @Test
    void nameReturnsFallback() {
        assertThat(service.name()).isEqualTo("FALLBACK");
    }
}
