package com.ainote.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolCallAuditorTest {

    private final ToolCallAuditor auditor = new ToolCallAuditor();

    @Test
    void validateKeepsResponseWhenToolCallBacksActionClaim() {
        ChatMemoryStore memoryStore = mock(ChatMemoryStore.class);
        when(memoryStore.getMessages("memory-1")).thenReturn(List.of(
                AiMessage.from("before"),
                ToolExecutionResultMessage.from("call-1", "noteAction", "created")));

        String response = "已创建笔记 123e4567-e89b-12d3-a456-426614174000。";

        assertThat(auditor.validate(response, "memory-1", memoryStore, 1)).isEqualTo(response);
    }

    @Test
    void validateCorrectsActionClaimWithoutToolCall() {
        ChatMemoryStore memoryStore = mock(ChatMemoryStore.class);
        when(memoryStore.getMessages("memory-1")).thenReturn(List.of(
                AiMessage.from("before"),
                AiMessage.from("after")));

        String result = auditor.validate(
                "已删除笔记 123e4567-e89b-12d3-a456-426614174000。",
                "memory-1",
                memoryStore,
                1);

        assertThat(result).contains("重新为您处理");
    }

    @Test
    void validateKeepsResponseWithoutActionClaim() {
        ChatMemoryStore memoryStore = mock(ChatMemoryStore.class);
        when(memoryStore.getMessages("memory-1")).thenReturn(List.of(AiMessage.from("before")));

        assertThat(auditor.validate("这是普通回答", "memory-1", memoryStore, 1)).contains("普通回答");
    }

    @Test
    void validateReturnsOriginalWhenMemoryStoreFails() {
        ChatMemoryStore memoryStore = mock(ChatMemoryStore.class);
        when(memoryStore.getMessages("memory-1")).thenThrow(new RuntimeException("store down"));

        String response = "已创建笔记 123e4567-e89b-12d3-a456-426614174000。";

        assertThat(auditor.validate(response, "memory-1", memoryStore, 0)).isEqualTo(response);
    }
}
