package com.ainote.app.service;

import com.ainote.app.entity.MemoryEvent;
import com.ainote.app.entity.SemanticMemory;
import com.ainote.app.model.memory.MemoryForgetRequest;
import com.ainote.app.model.memory.MemoryForgetResponse;
import com.ainote.app.model.memory.MemoryEventListResponse;
import com.ainote.app.model.memory.MemoryListResponse;
import com.ainote.app.model.memory.MemoryUpdateRequest;
import com.ainote.app.repository.MemoryEventRepository;
import com.ainote.app.repository.SemanticMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryControlServiceTest {

    private SemanticMemoryRepository semanticMemoryRepository;
    private MemoryEventRepository memoryEventRepository;
    private MemoryControlService service;

    @BeforeEach
    void setUp() {
        semanticMemoryRepository = mock(SemanticMemoryRepository.class);
        memoryEventRepository = mock(MemoryEventRepository.class);
        service = new MemoryControlService(semanticMemoryRepository, memoryEventRepository);
    }

    @Test
    void listMemoriesReturnsOnlyCurrentUserRowsFromRepository() {
        SemanticMemory memory = memory("user-1", 10L, "active");
        when(semanticMemoryRepository.searchUserMemories(
                eq("user-1"),
                eq("preference"),
                eq("active"),
                eq("markdown"),
                any(PageRequest.class)))
                .thenReturn(List.of(memory));

        MemoryListResponse response = service.listMemories(
                "user-1", "preference", "active", "markdown", null);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).id()).isEqualTo(10L);
        verify(semanticMemoryRepository).searchUserMemories(
                eq("user-1"),
                eq("preference"),
                eq("active"),
                eq("markdown"),
                any(PageRequest.class));
    }

    @Test
    void updateMemoryPatchesContentAndStatusAndRecordsEvent() {
        SemanticMemory memory = memory("user-1", 11L, "active");
        memory.setContent("old content");
        when(semanticMemoryRepository.findByIdAndUserId(11L, "user-1")).thenReturn(Optional.of(memory));
        when(semanticMemoryRepository.save(memory)).thenReturn(memory);

        MemoryUpdateRequest request = new MemoryUpdateRequest(
                "updated content", "disabled", "preference", "user", 0.7, "user corrected memory");

        service.updateMemory("user-1", 11L, request);

        assertThat(memory.getContent()).isEqualTo("updated content");
        assertThat(memory.getStatus()).isEqualTo("disabled");
        assertThat(memory.getMemoryType()).isEqualTo("preference");
        assertThat(memory.getScope()).isEqualTo("user");
        assertThat(memory.getConfidence()).isEqualTo(0.7);

        ArgumentCaptor<MemoryEvent> eventCaptor = ArgumentCaptor.forClass(MemoryEvent.class);
        verify(memoryEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getUserId()).isEqualTo("user-1");
        assertThat(eventCaptor.getValue().getMemoryId()).isEqualTo(11L);
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("UPDATED");
        assertThat(eventCaptor.getValue().getActor()).isEqualTo("user");
        assertThat(eventCaptor.getValue().getReason()).isEqualTo("user corrected memory");
    }

    @Test
    void listEventsReturnsCurrentUserLedgerRowsWithOptionalMemoryFilter() {
        MemoryEvent event = event("user-1", 11L, "CREATED");
        when(memoryEventRepository.findByUserIdAndMemoryIdOrderByCreatedAtDesc(
                eq("user-1"), eq(11L), any(PageRequest.class)))
                .thenReturn(List.of(event));

        MemoryEventListResponse response = service.listEvents("user-1", 11L, 25);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).memoryId()).isEqualTo(11L);
        assertThat(response.items().get(0).eventType()).isEqualTo("CREATED");
        assertThat(response.items().get(0).traceId()).isEqualTo("trace-11");
        verify(memoryEventRepository).findByUserIdAndMemoryIdOrderByCreatedAtDesc(
                eq("user-1"), eq(11L), any(PageRequest.class));
    }

    @Test
    void listEventsReturnsRecentCurrentUserLedgerRowsWithoutMemoryFilter() {
        MemoryEvent event = event("user-1", 11L, "CREATED");
        when(memoryEventRepository.findByUserIdOrderByCreatedAtDesc(
                eq("user-1"), any(PageRequest.class)))
                .thenReturn(List.of(event));

        MemoryEventListResponse response = service.listEvents("user-1", null, 50);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).eventType()).isEqualTo("CREATED");
        assertThat(response.items().get(0).traceId()).isEqualTo("trace-11");
        verify(memoryEventRepository).findByUserIdOrderByCreatedAtDesc(
                eq("user-1"), any(PageRequest.class));
    }

    @Test
    void deleteMemorySoftDeletesAndRecordsEvent() {
        SemanticMemory memory = memory("user-1", 12L, "active");
        when(semanticMemoryRepository.findByIdAndUserId(12L, "user-1")).thenReturn(Optional.of(memory));
        when(semanticMemoryRepository.save(memory)).thenReturn(memory);

        service.deleteMemory("user-1", 12L);

        assertThat(memory.getStatus()).isEqualTo("deleted");
        verify(semanticMemoryRepository).save(memory);
        ArgumentCaptor<MemoryEvent> eventCaptor = ArgumentCaptor.forClass(MemoryEvent.class);
        verify(memoryEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("DELETED");
        assertThat(eventCaptor.getValue().getMemoryId()).isEqualTo(12L);
    }

    @Test
    void deleteMemoryDoesNotTouchOtherUsersRows() {
        when(semanticMemoryRepository.findByIdAndUserId(12L, "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteMemory("user-1", 12L))
                .isInstanceOf(NoSuchElementException.class);

        verify(semanticMemoryRepository, never()).save(any());
        verify(memoryEventRepository, never()).save(any());
    }

    @Test
    void forgetMemoriesDeletesOnlyCurrentUserRequestedRows() {
        SemanticMemory memory = memory("user-1", 13L, "active");
        when(semanticMemoryRepository.findByUserIdAndIdIn("user-1", List.of(13L)))
                .thenReturn(List.of(memory));
        when(semanticMemoryRepository.save(memory)).thenReturn(memory);

        MemoryForgetResponse response = service.forgetMemories(
                "user-1",
                new MemoryForgetRequest(List.of(13L), null, "forget this"));

        assertThat(response.deletedCount()).isEqualTo(1);
        assertThat(memory.getStatus()).isEqualTo("deleted");
        ArgumentCaptor<MemoryEvent> eventCaptor = ArgumentCaptor.forClass(MemoryEvent.class);
        verify(memoryEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("DELETED");
        assertThat(eventCaptor.getValue().getReason()).isEqualTo("forget this");
    }

    private static SemanticMemory memory(String userId, Long id, String status) {
        SemanticMemory memory = new SemanticMemory();
        memory.setId(id);
        memory.setUserId(userId);
        memory.setCategory("preference");
        memory.setMemoryType("preference");
        memory.setScope("user");
        memory.setStatus(status);
        memory.setContent("prefers markdown");
        memory.setConfidence(0.9);
        return memory;
    }

    private static MemoryEvent event(String userId, Long memoryId, String eventType) {
        MemoryEvent event = new MemoryEvent();
        event.setId(101L);
        event.setUserId(userId);
        event.setMemoryId(memoryId);
        event.setEventType(eventType);
        event.setActor("assistant");
        event.setReason("explicit_memory");
        event.setAfterJson("{\"content\":\"prefers markdown\"}");
        event.setTraceId("trace-" + memoryId);
        return event;
    }
}
