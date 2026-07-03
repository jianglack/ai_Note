package com.ainote.app.memory;

import com.ainote.app.entity.UserMemory;
import com.ainote.app.config.MemoryProperties;
import com.ainote.app.repository.EpisodicMemoryRepository;
import com.ainote.app.repository.UserMemoryRepository;
import com.ainote.app.service.MemoryExtractionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
@DisplayName("ReliableChatMemoryStore deferred flush tests")
class ReliableChatMemoryStoreFlushTest {
    private UserMemoryRepository memoryRepository;
    private EpisodicMemoryRepository episodicMemoryRepository;
    private MemoryExtractionService memoryExtractionService;
    private MemoryProperties memoryProperties;
    private ReliableChatMemoryStore store;

    @BeforeEach
    void setUp() {
        memoryRepository = mock(UserMemoryRepository.class);
        episodicMemoryRepository = mock(EpisodicMemoryRepository.class);
        memoryExtractionService = mock(MemoryExtractionService.class);
        memoryProperties = new MemoryProperties();
        store = new ReliableChatMemoryStore(
                memoryRepository, episodicMemoryRepository, memoryProperties,
                new ObjectMapper(), memoryExtractionService);
    }

    @AfterEach
    void cleanup() {
        DeferredMemoryState.clear();
    }

    @SuppressWarnings("unchecked")
    @Test
    void deferredModeBuffersAndFlushes() {
        DeferredMemoryState.begin();

        store.updateMessages("user-1", List.of(UserMessage.from("msg1")));
        store.updateMessages("user-1", List.of(UserMessage.from("msg1"), UserMessage.from("msg2")));
        store.updateMessages("user-1", List.of(
                UserMessage.from("msg1"), UserMessage.from("msg2"), UserMessage.from("msg3")));

        verify(memoryRepository, never()).deleteByUserId(anyString());
        verify(memoryRepository, never()).saveAll(anyList());
        verify(memoryRepository, never()).findAllByUserIdOrderByCreatedAtAsc(anyString());

        when(memoryRepository.findAllByUserIdOrderByCreatedAtAsc("user-1"))
                .thenReturn(new ArrayList<>());
        store.flushDeferredWrites();

        verify(memoryRepository, never()).deleteByUserId("user-1");

        ArgumentCaptor<List<UserMemory>> captor = ArgumentCaptor.forClass(List.class);
        verify(memoryRepository, times(1)).saveAll(captor.capture());
        List<UserMemory> savedBatch = captor.getValue();

        assertThat(savedBatch).hasSize(3);
        assertThat(savedBatch.get(0).getContent()).isEqualTo("msg1");
        assertThat(savedBatch.get(0).getSequenceNumber()).isEqualTo(0);
        assertThat(savedBatch.get(1).getContent()).isEqualTo("msg2");
        assertThat(savedBatch.get(1).getSequenceNumber()).isEqualTo(1);
        assertThat(savedBatch.get(2).getContent()).isEqualTo("msg3");
        assertThat(savedBatch.get(2).getSequenceNumber()).isEqualTo(2);
    }

    @Test
    void deferredModeReturnsBufferedMessagesDuringAgentLoop() {
        DeferredMemoryState.begin();
        ReliableChatMemoryStore.IN_AGENT_LOOP.set(true);

        store.updateMessages("user-1", List.of(UserMessage.from("msg1")));

        List<ChatMessage> messages = store.getMessages("user-1");

        assertThat(messages).hasSize(1);
        assertThat(((UserMessage) messages.get(0)).singleText()).isEqualTo("msg1");
        verify(memoryRepository, never()).findAllByUserIdOrderByCreatedAtAsc("user-1");
    }

    @Test
    void immediateModeAppendsDirectly() {
        when(memoryRepository.findAllByUserIdOrderByCreatedAtAsc("user-1"))
                .thenReturn(new ArrayList<>());

        store.updateMessages("user-1", List.of(UserMessage.from("msg1")));

        verify(memoryRepository, never()).deleteByUserId("user-1");
        verify(memoryRepository, times(1)).saveAll(anyList());
    }

    @SuppressWarnings("unchecked")
    @Test
    void appendModeOnlySavesNewSuffix() {
        when(memoryRepository.findAllByUserIdOrderByCreatedAtAsc("user-1"))
                .thenReturn(List.of(
                        userMemory("msg1", 0),
                        userMemory("msg2", 1)));

        store.updateMessages("user-1", List.of(
                UserMessage.from("msg1"),
                UserMessage.from("msg2"),
                UserMessage.from("msg3")));

        verify(memoryRepository, never()).deleteByUserId("user-1");

        ArgumentCaptor<List<UserMemory>> captor = ArgumentCaptor.forClass(List.class);
        verify(memoryRepository).saveAll(captor.capture());
        List<UserMemory> savedBatch = captor.getValue();

        assertThat(savedBatch).hasSize(1);
        assertThat(savedBatch.get(0).getContent()).isEqualTo("msg3");
        assertThat(savedBatch.get(0).getSequenceNumber()).isEqualTo(2);
    }

    @SuppressWarnings("unchecked")
    @Test
    void trimmedWindowKeepsStoredHistoryAndSummarizesTrimmedPrefix() {
        when(memoryRepository.findAllByUserIdOrderByCreatedAtAsc("user-1"))
                .thenReturn(List.of(
                        userMemory("old1", 0),
                        userMemory("old2", 1),
                        userMemory("msg2", 2)));

        store.updateMessages("user-1", List.of(
                UserMessage.from("msg2"),
                UserMessage.from("msg3")));

        verify(memoryRepository, never()).deleteByUserId("user-1");
        verify(memoryExtractionService).generateEpisodicSummaryFromText(
                eq("user-1"),
                contains("old1"),
                eq(2));

        ArgumentCaptor<List<UserMemory>> captor = ArgumentCaptor.forClass(List.class);
        verify(memoryRepository).saveAll(captor.capture());
        List<UserMemory> savedBatch = captor.getValue();

        assertThat(savedBatch).hasSize(1);
        assertThat(savedBatch.get(0).getContent()).isEqualTo("msg3");
        assertThat(savedBatch.get(0).getSequenceNumber()).isEqualTo(3);
    }

    @Test
    void legacyRewriteModeKeepsRollbackPath() {
        memoryProperties.getChatHistory().setWriteMode(MemoryProperties.ChatHistoryWriteMode.LEGACY_REWRITE);
        when(memoryRepository.findAllByUserIdOrderByCreatedAtAsc("user-1"))
                .thenReturn(new ArrayList<>());

        store.updateMessages("user-1", List.of(UserMessage.from("msg1")));

        verify(memoryRepository, times(1)).deleteByUserId("user-1");
        verify(memoryRepository, times(1)).saveAll(anyList());
    }

    @Test
    void flushExceptionPropagates() {
        DeferredMemoryState.begin();

        store.updateMessages("user-1", List.of(UserMessage.from("msg1")));

        when(memoryRepository.findAllByUserIdOrderByCreatedAtAsc("user-1"))
                .thenReturn(new ArrayList<>());
        doThrow(new RuntimeException("DB down")).when(memoryRepository).saveAll(anyList());

        assertThatThrownBy(() -> store.flushDeferredWrites())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB down");
    }

    private UserMemory userMemory(String content, int sequenceNumber) {
        UserMemory memory = new UserMemory();
        memory.setUserId("user-1");
        memory.setMessageType("USER");
        memory.setContent(content);
        memory.setSequenceNumber(sequenceNumber);
        return memory;
    }

    @AfterEach
    void cleanupAgentLoopFlag() {
        ReliableChatMemoryStore.IN_AGENT_LOOP.remove();
    }
}
