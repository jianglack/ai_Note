package com.ainote.app.memory;

import com.ainote.app.entity.UserMemory;
import com.ainote.app.repository.EpisodicMemoryRepository;
import com.ainote.app.repository.UserMemoryRepository;
import com.ainote.app.service.MemoryExtractionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReliableChatMemoryStore deferred flush tests")
class ReliableChatMemoryStoreFlushTest {

    @Mock private UserMemoryRepository memoryRepository;
    @Mock private EpisodicMemoryRepository episodicMemoryRepository;
    @Mock private MemoryExtractionService memoryExtractionService;

    private ReliableChatMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new ReliableChatMemoryStore(
                memoryRepository, episodicMemoryRepository,
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

        verify(memoryRepository, times(1)).deleteByUserId("user-1");

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
    void immediateModeWritesDirectly() {
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
        doThrow(new RuntimeException("DB down")).when(memoryRepository).deleteByUserId("user-1");

        assertThatThrownBy(() -> store.flushDeferredWrites())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB down");
    }
}
