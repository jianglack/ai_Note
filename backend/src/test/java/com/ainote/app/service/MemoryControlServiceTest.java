package com.ainote.app.service;

import com.ainote.app.entity.MemoryEvent;
import com.ainote.app.entity.SemanticMemory;
import com.ainote.app.config.MemoryProperties;
import com.ainote.app.model.memory.MemoryDeleteProofResponse;
import com.ainote.app.model.memory.MemoryForgetRequest;
import com.ainote.app.model.memory.MemoryForgetResponse;
import com.ainote.app.model.memory.MemoryEventListResponse;
import com.ainote.app.model.memory.MemoryExportResponse;
import com.ainote.app.model.memory.MemoryListResponse;
import com.ainote.app.model.memory.MemoryRetentionPurgeResponse;
import com.ainote.app.model.memory.MemoryUpdateRequest;
import com.ainote.app.repository.MemoryEventRepository;
import com.ainote.app.repository.SemanticMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryControlServiceTest {

    private SemanticMemoryRepository semanticMemoryRepository;
    private MemoryEventRepository memoryEventRepository;
    private MemoryMetricsService memoryMetricsService;
    private MemoryControlService service;

    @BeforeEach
    void setUp() {
        semanticMemoryRepository = mock(SemanticMemoryRepository.class);
        memoryEventRepository = mock(MemoryEventRepository.class);
        memoryMetricsService = mock(MemoryMetricsService.class);
        service = new MemoryControlService(
                semanticMemoryRepository,
                memoryEventRepository,
                new MemoryPrivacyService(),
                new MemoryProperties(),
                memoryMetricsService);
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
    void hasActiveMemoriesUsesGovernedActiveOnlyQuery() {
        when(semanticMemoryRepository.searchUserMemories(
                eq("user-1"),
                eq(""),
                eq("active"),
                eq(""),
                any(PageRequest.class)))
                .thenReturn(List.of(memory("user-1", 12L, "active")));

        assertThat(service.hasActiveMemories("user-1")).isTrue();
        assertThat(service.hasActiveMemories(" ")).isFalse();
    }

    @Test
    void listMemoriesReturnsGovernanceMetadataJson() {
        SemanticMemory memory = memory("user-1", 21L, "active");
        memory.setMetadataJson("{\"capture_reason\":\"explicit memory request\",\"policy_signals\":[\"explicit_remember\"]}");
        when(semanticMemoryRepository.searchUserMemories(
                eq("user-1"),
                eq(null),
                eq(null),
                eq(null),
                any(PageRequest.class)))
                .thenReturn(List.of(memory));

        MemoryListResponse response = service.listMemories("user-1", null, null, null, null);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).metadataJson())
                .isEqualTo("{\"capture_reason\":\"explicit memory request\",\"policy_signals\":[\"explicit_remember\"]}");
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
    void updateMemoryRejectsSensitiveContent() {
        SemanticMemory memory = memory("user-1", 11L, "active");
        when(semanticMemoryRepository.findByIdAndUserId(11L, "user-1")).thenReturn(Optional.of(memory));

        MemoryUpdateRequest request = new MemoryUpdateRequest(
                "my email is alice@example.com", null, null, null, null, "user edit");

        assertThatThrownBy(() -> service.updateMemory("user-1", 11L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sensitive data");

        verify(semanticMemoryRepository, never()).save(any());
        verify(memoryEventRepository, never()).save(any());
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
        verify(memoryMetricsService).recordUserDeletion("soft_delete", 1);
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
        verify(memoryMetricsService).recordUserDeletion("forget", 1);
    }

    @Test
    void forgetAllActiveMemoriesDeletesEveryCurrentUserActiveRow() {
        SemanticMemory first = memory("user-1", 17L, "active");
        SemanticMemory second = memory("user-1", 18L, null);
        when(semanticMemoryRepository.searchUserMemories(
                eq("user-1"),
                eq(null),
                eq("active"),
                eq(null),
                any(PageRequest.class)))
                .thenReturn(List.of(first, second))
                .thenReturn(List.of());
        when(semanticMemoryRepository.save(first)).thenReturn(first);
        when(semanticMemoryRepository.save(second)).thenReturn(second);

        MemoryForgetResponse response = service.forgetAllActiveMemories(
                "user-1", "natural language forget-all request");

        assertThat(response.deletedCount()).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo("deleted");
        assertThat(second.getStatus()).isEqualTo("deleted");
        verify(semanticMemoryRepository, times(2)).save(any(SemanticMemory.class));

        ArgumentCaptor<MemoryEvent> eventCaptor = ArgumentCaptor.forClass(MemoryEvent.class);
        verify(memoryEventRepository, times(2)).save(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(MemoryEvent::getMemoryId)
                .containsExactly(17L, 18L);
        assertThat(eventCaptor.getAllValues())
                .extracting(MemoryEvent::getEventType)
                .containsOnly("DELETED");
        verify(memoryMetricsService).recordUserDeletion("forget_all", 2);
    }

    @Test
    void exportMemoriesRedactsSensitiveValuesAndReportsSummary() {
        SemanticMemory memory = memory("user-1", 14L, "active");
        memory.setContent("backup email alice@example.com and card 4111111111111111");
        memory.setEvidenceExcerpt("phone 13812345678");
        memory.setMetadataJson("{\"token\":\"sk-abcdefghijklmnopqrstuvwxyz\"}");
        when(semanticMemoryRepository.searchUserMemories(
                eq("user-1"),
                eq(null),
                eq(null),
                eq(null),
                any(PageRequest.class)))
                .thenReturn(List.of(memory));

        MemoryExportResponse response = service.exportMemories("user-1");

        assertThat(response.schemaVersion()).isEqualTo("memory-export-json-v1");
        assertThat(response.itemCount()).isEqualTo(1);
        assertThat(response.redacted()).isTrue();
        assertThat(response.items().get(0).content()).contains("[REDACTED:email]", "[REDACTED:payment_card]");
        assertThat(response.items().get(0).evidenceExcerpt()).contains("[REDACTED:phone]");
        assertThat(response.items().get(0).metadataJson()).contains("[REDACTED:credential]");
        assertThat(response.redactionSummary())
                .containsEntry("email", 1)
                .containsEntry("payment_card", 1)
                .containsEntry("phone", 1)
                .containsEntry("credential", 1);
    }

    @Test
    void hardDeleteDeletesOnlyCurrentUserRowsAndReturnsProof() {
        SemanticMemory memory = memory("user-1", 15L, "active");
        memory.setContent("backup email alice@example.com");
        memory.setContentHash("hash-15");
        when(semanticMemoryRepository.findByUserIdAndIdIn("user-1", List.of(15L)))
                .thenReturn(List.of(memory));

        MemoryDeleteProofResponse response = service.hardDeleteMemories(
                "user-1",
                new MemoryForgetRequest(List.of(15L), null, "hard delete requested"));

        assertThat(response.deletedCount()).isEqualTo(1);
        assertThat(response.deletedMemoryIds()).containsExactly(15L);
        assertThat(response.contentHashes()).containsExactly("hash-15");
        assertThat(response.status()).isEqualTo("completed");
        assertThat(response.proofJson()).contains("hash-15").doesNotContain("alice@example.com");
        verify(semanticMemoryRepository).deleteByUserIdAndIdIn("user-1", List.of(15L));

        ArgumentCaptor<MemoryEvent> eventCaptor = ArgumentCaptor.forClass(MemoryEvent.class);
        verify(memoryEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("HARD_DELETED");
        assertThat(eventCaptor.getValue().getBeforeJson()).contains("[REDACTED:email]", "hash-15");
        assertThat(eventCaptor.getValue().getBeforeJson()).doesNotContain("alice@example.com");
        verify(memoryMetricsService).recordUserDeletion("hard_delete", 1);
    }

    @Test
    void hardDeleteDoesNotTouchOtherUsersRows() {
        when(semanticMemoryRepository.findByUserIdAndIdIn("user-1", List.of(99L)))
                .thenReturn(List.of());

        MemoryDeleteProofResponse response = service.hardDeleteMemories(
                "user-1",
                new MemoryForgetRequest(List.of(99L), null, "hard delete requested"));

        assertThat(response.deletedCount()).isZero();
        verify(semanticMemoryRepository, never()).deleteByUserIdAndIdIn(eq("user-1"), any());
        verify(semanticMemoryRepository, never()).deleteByIds(any());
        verify(memoryEventRepository, never()).save(any());
    }

    @Test
    void purgeExpiredDeletedMemoriesRecordsEventsAndDeletesEligibleRows() {
        LocalDateTime now = LocalDateTime.parse("2026-07-10T10:00:00");
        LocalDateTime cutoff = LocalDateTime.parse("2026-06-10T10:00:00");
        SemanticMemory memory = memory("user-1", 16L, "deleted");
        memory.setContent("old deleted preference");
        when(semanticMemoryRepository.findByStatusInAndUpdatedAtBefore(
                eq(List.of("deleted", "retracted")),
                eq(cutoff),
                any(PageRequest.class)))
                .thenReturn(List.of(memory));

        MemoryRetentionPurgeResponse response = service.purgeExpiredDeletedMemories(now);

        assertThat(response.purgedCount()).isEqualTo(1);
        assertThat(response.purgedMemoryIds()).containsExactly(16L);
        assertThat(response.cutoff()).isEqualTo(cutoff);
        verify(semanticMemoryRepository).deleteAllByIdInBatch(List.of(16L));

        ArgumentCaptor<MemoryEvent> eventCaptor = ArgumentCaptor.forClass(MemoryEvent.class);
        verify(memoryEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("RETENTION_PURGED");
        assertThat(eventCaptor.getValue().getActor()).isEqualTo("system");
        verify(memoryMetricsService).recordRetentionPurge(1);
    }

    @Test
    void compliancePostureReportsEncryptionActionRequiredByDefault() {
        var posture = service.compliancePosture();

        assertThat(posture.piiDetectionEnabled()).isTrue();
        assertThat(posture.exportRedactionEnabled()).isTrue();
        assertThat(posture.deletedMemoryRetentionDays()).isEqualTo(30);
        assertThat(posture.atRestEncryptionRequired()).isTrue();
        assertThat(posture.atRestEncryptionConfirmed()).isFalse();
        assertThat(posture.status()).isEqualTo("action_required");
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
