package com.ainote.app.memory;

import com.ainote.app.entity.UserMemory;
import com.ainote.app.entity.ChatMemoryHead;
import com.ainote.app.entity.ChatMemoryCompactionJob;
import com.ainote.app.config.MemoryProperties;
import com.ainote.app.repository.ChatMemoryHeadRepository;
import com.ainote.app.repository.ChatMemoryCompactionJobRepository;
import com.ainote.app.repository.UserMemoryRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
    private ChatMemoryHeadRepository headRepository;
    private ChatMemoryCompactionJobRepository compactionJobRepository;
    private ShortTermMemoryMetrics metrics;
    private MemoryProperties memoryProperties;
    private ReliableChatMemoryStore store;

    @BeforeEach
    void setUp() {
        memoryRepository = mock(UserMemoryRepository.class);
        headRepository = mock(ChatMemoryHeadRepository.class);
        compactionJobRepository = mock(ChatMemoryCompactionJobRepository.class);
        metrics = mock(ShortTermMemoryMetrics.class);
        memoryProperties = new MemoryProperties();
        memoryProperties.getChatHistory().setFlushRetryDelayMs(0);
        ChatMemoryHead head = new ChatMemoryHead();
        head.setUserId("user-1");
        head.setNextSequenceNumber(0);
        when(headRepository.findByUserIdForUpdate("user-1")).thenReturn(Optional.of(head));
        when(memoryRepository.findByUserIdAndSequenceRange(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of());
        store = new ReliableChatMemoryStore(
                memoryRepository, headRepository, compactionJobRepository, memoryProperties,
                new ObjectMapper(), metrics);
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
        verify(memoryRepository, never()).findModelWindowByUserIdOrderBySequenceDesc(anyString(), any());

        when(memoryRepository.findModelWindowByUserIdOrderBySequenceDesc(eq("user-1"), any()))
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
        verify(memoryRepository, never()).findModelWindowByUserIdOrderBySequenceDesc(eq("user-1"), any());
    }

    @Test
    void getMessagesUsesBoundedModelWindowQueryAndNeverFullHistoryQuery() {
        memoryProperties.getChatHistory().setModelWindowMaxMessages(12);
        when(memoryRepository.findModelWindowByUserIdOrderBySequenceDesc(eq("user-1"), any()))
                .thenReturn(List.of(userMemory("latest", 11)));

        List<ChatMessage> messages = store.getMessages("user-1");

        assertThat(messages).hasSize(1);
        ArgumentCaptor<org.springframework.data.domain.Pageable> pageable =
                ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(memoryRepository).findModelWindowByUserIdOrderBySequenceDesc(eq("user-1"), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(12);
        verify(memoryRepository, never()).findAllByUserIdOrderByCreatedAtAsc(anyString());
    }

    @Test
    void immediateModeAppendsDirectly() {
        when(memoryRepository.findModelWindowByUserIdOrderBySequenceDesc(eq("user-1"), any()))
                .thenReturn(new ArrayList<>());

        store.updateMessages("user-1", List.of(UserMessage.from("msg1")));

        verify(memoryRepository, never()).deleteByUserId("user-1");
        verify(memoryRepository, times(1)).saveAll(anyList());
    }

    @SuppressWarnings("unchecked")
    @Test
    void appendModeOnlySavesNewSuffix() {
        when(memoryRepository.findModelWindowByUserIdOrderBySequenceDesc(eq("user-1"), any()))
                .thenReturn(List.of(userMemory("msg2", 1), userMemory("msg1", 0)));

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
    void trimmedWindowKeepsStoredHistoryAndEnqueuesOneCompactionRange() {
        memoryProperties.getChatHistory().setCompactionMinUserTurns(2);
        UserMemory old1 = userMemory("old1", 0);
        UserMemory old2 = userMemory("old2", 1);
        UserMemory msg2 = userMemory("msg2", 2);
        when(memoryRepository.findModelWindowByUserIdOrderBySequenceDesc(eq("user-1"), any()))
                .thenReturn(List.of(msg2, old2, old1));
        when(memoryRepository.findByUserIdAndSequenceRange("user-1", 0, 1))
                .thenReturn(List.of(old1, old2));

        store.updateMessages("user-1", List.of(
                UserMessage.from("msg2"),
                UserMessage.from("msg3")));

        verify(memoryRepository, never()).deleteByUserId("user-1");
        assertThat(old1.getTrimmedAt()).isNotNull();
        assertThat(old2.getTrimmedAt()).isNotNull();
        verify(compactionJobRepository).save(any(ChatMemoryCompactionJob.class));

        ArgumentCaptor<List<UserMemory>> captor = ArgumentCaptor.forClass(List.class);
        verify(memoryRepository, times(2)).saveAll(captor.capture());
        List<UserMemory> savedBatch = captor.getAllValues().get(1);

        assertThat(savedBatch).hasSize(1);
        assertThat(savedBatch.get(0).getContent()).isEqualTo("msg3");
        assertThat(savedBatch.get(0).getSequenceNumber()).isEqualTo(3);
    }

    @Test
    void compactionRangeStopsAtConfiguredCharacterBudget() {
        memoryProperties.getChatHistory().setCompactionMaxSourceCharacters(1000);
        UserMemory old1 = userMemory("x".repeat(900), 0);
        UserMemory old2 = userMemory("y".repeat(900), 1);
        UserMemory current = userMemory("current", 2);
        when(memoryRepository.findModelWindowByUserIdOrderBySequenceDesc(eq("user-1"), any()))
                .thenReturn(List.of(current, old2, old1));
        when(memoryRepository.findByUserIdAndSequenceRange("user-1", 0, 1))
                .thenReturn(List.of(old1, old2));

        store.updateMessages("user-1", List.of(
                UserMessage.from("current"), UserMessage.from("new")));

        ArgumentCaptor<ChatMemoryCompactionJob> job = ArgumentCaptor.forClass(ChatMemoryCompactionJob.class);
        verify(compactionJobRepository).save(job.capture());
        assertThat(job.getValue().getFromSequence()).isZero();
        assertThat(job.getValue().getToSequence()).isZero();
    }

    @Test
    void legacyRewriteModeKeepsRollbackPath() {
        memoryProperties.getChatHistory().setWriteMode(MemoryProperties.ChatHistoryWriteMode.LEGACY_REWRITE);
        when(memoryRepository.findModelWindowByUserIdOrderBySequenceDesc(eq("user-1"), any()))
                .thenReturn(new ArrayList<>());

        store.updateMessages("user-1", List.of(UserMessage.from("msg1")));

        verify(memoryRepository, times(1)).deleteByUserId("user-1");
        verify(memoryRepository, times(1)).saveAll(anyList());
    }

    @Test
    void flushExceptionPropagates() {
        DeferredMemoryState.begin();

        store.updateMessages("user-1", List.of(UserMessage.from("msg1")));

        when(memoryRepository.findModelWindowByUserIdOrderBySequenceDesc(eq("user-1"), any()))
                .thenReturn(new ArrayList<>());
        doThrow(new RuntimeException("DB down")).when(memoryRepository).saveAll(anyList());

        assertThatThrownBy(() -> store.flushDeferredWrites())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB down");
        verify(memoryRepository, times(3)).saveAll(anyList());
        verify(metrics, times(2)).recordFlushRetry();
        verify(metrics).recordFlushFailure();
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
