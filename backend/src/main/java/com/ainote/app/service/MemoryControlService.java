package com.ainote.app.service;

import com.ainote.app.config.MemoryProperties;
import com.ainote.app.entity.MemoryEvent;
import com.ainote.app.entity.SemanticMemory;
import com.ainote.app.model.memory.MemoryCompliancePostureResponse;
import com.ainote.app.model.memory.MemoryDeleteProofResponse;
import com.ainote.app.model.memory.MemoryEventListResponse;
import com.ainote.app.model.memory.MemoryEventResponse;
import com.ainote.app.model.memory.MemoryExportResponse;
import com.ainote.app.model.memory.MemoryForgetRequest;
import com.ainote.app.model.memory.MemoryForgetResponse;
import com.ainote.app.model.memory.MemoryListResponse;
import com.ainote.app.model.memory.MemoryRetentionPurgeResponse;
import com.ainote.app.model.memory.MemoryResponse;
import com.ainote.app.model.memory.MemoryUpdateRequest;
import com.ainote.app.repository.MemoryEventRepository;
import com.ainote.app.repository.SemanticMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional
public class MemoryControlService {

    private static final int PAGE_SIZE = 50;
    private static final String EXPORT_SCHEMA_VERSION = "memory-export-json-v1";

    private final SemanticMemoryRepository semanticMemoryRepository;
    private final MemoryEventRepository memoryEventRepository;
    private final MemoryPrivacyService privacyService;
    private final MemoryProperties memoryProperties;
    private final MemoryMetricsService memoryMetricsService;

    public MemoryControlService(SemanticMemoryRepository semanticMemoryRepository,
                                MemoryEventRepository memoryEventRepository) {
        this(semanticMemoryRepository, memoryEventRepository, new MemoryPrivacyService(), new MemoryProperties(),
                MemoryMetricsService.noop());
    }

    public MemoryControlService(SemanticMemoryRepository semanticMemoryRepository,
                                MemoryEventRepository memoryEventRepository,
                                MemoryPrivacyService privacyService,
                                MemoryProperties memoryProperties) {
        this(semanticMemoryRepository, memoryEventRepository, privacyService, memoryProperties, MemoryMetricsService.noop());
    }

    @Autowired
    public MemoryControlService(SemanticMemoryRepository semanticMemoryRepository,
                                MemoryEventRepository memoryEventRepository,
                                MemoryPrivacyService privacyService,
                                MemoryProperties memoryProperties,
                                MemoryMetricsService memoryMetricsService) {
        this.semanticMemoryRepository = semanticMemoryRepository;
        this.memoryEventRepository = memoryEventRepository;
        this.privacyService = privacyService == null ? new MemoryPrivacyService() : privacyService;
        this.memoryProperties = memoryProperties == null ? new MemoryProperties() : memoryProperties;
        this.memoryMetricsService = memoryMetricsService == null ? MemoryMetricsService.noop() : memoryMetricsService;
    }

    @Transactional(readOnly = true)
    public MemoryListResponse listMemories(String userId,
                                           String type,
                                           String status,
                                           String query,
                                           String cursor) {
        int page = parseCursor(cursor);
        List<SemanticMemory> rows = semanticMemoryRepository.searchUserMemories(
                userId,
                normalizeBlank(type),
                normalizeBlank(status),
                normalizeBlank(query),
                PageRequest.of(page, PAGE_SIZE + 1));

        boolean hasNext = rows.size() > PAGE_SIZE;
        List<MemoryResponse> items = rows.stream()
                .limit(PAGE_SIZE)
                .map(this::toResponse)
                .toList();
        return new MemoryListResponse(items, hasNext ? String.valueOf(page + 1) : null);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveMemories(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return !semanticMemoryRepository.searchUserMemories(
                userId,
                "",
                "active",
                "",
                PageRequest.of(0, 1)).isEmpty();
    }

    public MemoryResponse updateMemory(String userId, Long id, MemoryUpdateRequest request) {
        SemanticMemory memory = semanticMemoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NoSuchElementException("Memory not found: " + id));
        String before = snapshot(memory);

        if (request.content() != null) {
            assertSafeToStore(request.content());
            memory.setContent(request.content());
        }
        if (request.status() != null) {
            memory.setStatus(normalizeStatus(request.status()));
        }
        if (request.memoryType() != null) {
            memory.setMemoryType(normalizeLower(request.memoryType()));
        }
        if (request.scope() != null) {
            memory.setScope(normalizeLower(request.scope()));
        }
        if (request.confidence() != null) {
            memory.setConfidence(request.confidence());
        }

        SemanticMemory saved = semanticMemoryRepository.save(memory);
        recordEvent(userId, saved.getId(), "UPDATED", "user", request.reason(), before, snapshot(saved));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public MemoryEventListResponse listEvents(String userId, Long memoryId, Integer limit) {
        int size = normalizeLimit(limit);
        List<MemoryEvent> rows = memoryId == null
                ? memoryEventRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, size))
                : memoryEventRepository.findByUserIdAndMemoryIdOrderByCreatedAtDesc(
                        userId, memoryId, PageRequest.of(0, size));
        return new MemoryEventListResponse(rows.stream()
                .map(this::toEventResponse)
                .toList());
    }

    public void deleteMemory(String userId, Long id) {
        SemanticMemory memory = semanticMemoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NoSuchElementException("Memory not found: " + id));
        String before = snapshot(memory);
        memory.setStatus("deleted");
        SemanticMemory saved = semanticMemoryRepository.save(memory);
        recordEvent(userId, saved.getId(), "DELETED", "user", "user deleted memory", before, snapshot(saved));
        memoryMetricsService.recordUserDeletion("soft_delete", 1);
    }

    public MemoryForgetResponse forgetMemories(String userId, MemoryForgetRequest request) {
        List<SemanticMemory> matches = resolveForgetMatches(userId, request, PAGE_SIZE, "active");

        String reason = request == null ? null : request.reason();
        softDeleteMatches(userId, matches, reason);
        memoryMetricsService.recordUserDeletion("forget", matches.size());
        return new MemoryForgetResponse(matches.size());
    }

    public MemoryForgetResponse forgetAllActiveMemories(String userId, String reason) {
        int deleted = 0;
        while (true) {
            List<SemanticMemory> matches = semanticMemoryRepository.searchUserMemories(
                    userId,
                    null,
                    "active",
                    null,
                    PageRequest.of(0, PAGE_SIZE));
            if (matches.isEmpty()) {
                break;
            }
            deleted += softDeleteMatches(userId, matches, reason);
        }
        memoryMetricsService.recordUserDeletion("forget_all", deleted);
        return new MemoryForgetResponse(deleted);
    }

    @Transactional(readOnly = true)
    public MemoryExportResponse exportMemories(String userId) {
        boolean redactExports = memoryProperties.getPrivacy().isRedactExports();
        List<SemanticMemory> rows = semanticMemoryRepository.searchUserMemories(
                userId,
                null,
                null,
                null,
                PageRequest.of(0, memoryProperties.getPrivacy().getExportMaxItems()));
        List<MemoryResponse> items = rows.stream()
                .map(memory -> toResponse(memory, redactExports))
                .toList();
        return new MemoryExportResponse(
                items,
                null,
                LocalDateTime.now(),
                items.size(),
                redactExports,
                redactionSummary(rows),
                EXPORT_SCHEMA_VERSION);
    }

    public MemoryDeleteProofResponse hardDeleteMemories(String userId, MemoryForgetRequest request) {
        LocalDateTime requestedAt = LocalDateTime.now();
        String requestId = "memory-delete-" + UUID.randomUUID();
        List<SemanticMemory> matches = resolveForgetMatches(
                userId,
                request,
                memoryProperties.getPrivacy().getExportMaxItems(),
                null);
        List<Long> ids = matches.stream().map(SemanticMemory::getId).toList();
        List<String> contentHashes = matches.stream().map(this::contentHash).toList();
        String reason = request == null ? null : request.reason();

        for (SemanticMemory memory : matches) {
            recordEvent(
                    userId,
                    memory.getId(),
                    "HARD_DELETED",
                    "user",
                    reason,
                    privacySnapshot(memory),
                    proofEventJson(requestId, memory.getId(), contentHash(memory)));
        }
        if (!ids.isEmpty()) {
            semanticMemoryRepository.deleteByUserIdAndIdIn(userId, ids);
        }
        LocalDateTime completedAt = LocalDateTime.now();
        String proofJson = deleteProofJson(requestId, userId, ids, contentHashes, requestedAt, completedAt);
        memoryMetricsService.recordUserDeletion("hard_delete", ids.size());
        return new MemoryDeleteProofResponse(
                requestId,
                userId,
                ids,
                ids.size(),
                contentHashes,
                requestedAt,
                completedAt,
                "completed",
                proofJson);
    }

    public MemoryRetentionPurgeResponse purgeExpiredDeletedMemories(LocalDateTime now) {
        LocalDateTime effectiveNow = now == null ? LocalDateTime.now() : now;
        LocalDateTime cutoff = effectiveNow.minusDays(memoryProperties.getPrivacy().getDeletedMemoryRetentionDays());
        List<SemanticMemory> expired = semanticMemoryRepository.findByStatusInAndUpdatedAtBefore(
                List.of("deleted", "retracted"),
                cutoff,
                PageRequest.of(0, memoryProperties.getPrivacy().getExportMaxItems()));
        List<Long> ids = expired.stream().map(SemanticMemory::getId).toList();
        for (SemanticMemory memory : expired) {
            recordEvent(
                    memory.getUserId(),
                    memory.getId(),
                    "RETENTION_PURGED",
                    "system",
                    "deleted memory retention period elapsed",
                    privacySnapshot(memory),
                    "{\"status\":\"purged\",\"cutoff\":\"" + cutoff + "\"}");
        }
        if (!ids.isEmpty()) {
            semanticMemoryRepository.deleteAllByIdInBatch(ids);
        }
        memoryMetricsService.recordRetentionPurge(ids.size());
        return new MemoryRetentionPurgeResponse(
                ids.size(),
                ids,
                cutoff,
                effectiveNow,
                "completed");
    }

    @Transactional(readOnly = true)
    public MemoryCompliancePostureResponse compliancePosture() {
        MemoryProperties.Privacy privacy = memoryProperties.getPrivacy();
        boolean encryptionCompliant = !privacy.isAtRestEncryptionRequired()
                || privacy.isAtRestEncryptionConfirmed();
        return new MemoryCompliancePostureResponse(
                true,
                privacy.isRedactExports(),
                privacy.getDeletedMemoryRetentionDays(),
                privacy.isAtRestEncryptionRequired(),
                privacy.isAtRestEncryptionConfirmed(),
                nullSafe(privacy.getAtRestEncryptionKeyRef()),
                encryptionCompliant ? "compliant" : "action_required");
    }

    private void recordEvent(String userId,
                             Long memoryId,
                             String eventType,
                             String actor,
                             String reason,
                             String beforeJson,
                             String afterJson) {
        MemoryEvent event = new MemoryEvent();
        event.setUserId(userId);
        event.setMemoryId(memoryId);
        event.setEventType(eventType);
        event.setActor(actor);
        event.setReason(privacyService.redact(reason));
        event.setBeforeJson(beforeJson);
        event.setAfterJson(afterJson);
        memoryEventRepository.save(event);
    }

    private int softDeleteMatches(String userId, List<SemanticMemory> matches, String reason) {
        for (SemanticMemory memory : matches) {
            String before = snapshot(memory);
            memory.setStatus("deleted");
            SemanticMemory saved = semanticMemoryRepository.save(memory);
            recordEvent(userId, saved.getId(), "DELETED", "user", reason, before, snapshot(saved));
        }
        return matches.size();
    }

    private MemoryResponse toResponse(SemanticMemory memory) {
        return toResponse(memory, false);
    }

    private MemoryResponse toResponse(SemanticMemory memory, boolean redact) {
        return new MemoryResponse(
                memory.getId(),
                "semantic",
                memory.getMemoryType(),
                memory.getCategory(),
                redact ? privacyService.redact(memory.getContent()) : memory.getContent(),
                memory.getConfidence(),
                memory.getSource(),
                memory.getScope(),
                memory.getStatus(),
                memory.getSourceTraceId(),
                memory.getSourceMessageIds(),
                memory.getSourceToolCallId(),
                redact ? privacyService.redact(memory.getEvidenceExcerpt()) : memory.getEvidenceExcerpt(),
                redact ? privacyService.redact(memory.getMetadataJson()) : memory.getMetadataJson(),
                memory.getLastAccessedAt(),
                memory.getAccessCount(),
                memory.getSupersedesId(),
                memory.getCreatedAt(),
                memory.getUpdatedAt());
    }

    private MemoryEventResponse toEventResponse(MemoryEvent event) {
        return new MemoryEventResponse(
                event.getId(),
                event.getMemoryId(),
                event.getEventType(),
                event.getActor(),
                event.getReason(),
                event.getBeforeJson(),
                event.getAfterJson(),
                event.getTraceId(),
                event.getCreatedAt());
    }

    private String snapshot(SemanticMemory memory) {
        return privacySnapshot(memory);
    }

    private String privacySnapshot(SemanticMemory memory) {
        return "{\"id\":" + memory.getId()
                + ",\"status\":\"" + nullSafe(memory.getStatus())
                + "\",\"memoryType\":\"" + nullSafe(memory.getMemoryType())
                + "\",\"content\":\"" + escapeJson(privacyService.redact(nullSafe(memory.getContent())))
                + "\",\"contentHash\":\"" + contentHash(memory)
                + "\"}";
    }

    private List<SemanticMemory> resolveForgetMatches(String userId,
                                                      MemoryForgetRequest request,
                                                      int limit,
                                                      String status) {
        if (request == null) {
            return List.of();
        }
        List<SemanticMemory> matches = new ArrayList<>();
        if (request.memoryIds() != null && !request.memoryIds().isEmpty()) {
            matches.addAll(semanticMemoryRepository.findByUserIdAndIdIn(userId, request.memoryIds()));
        } else if (request.query() != null && !request.query().isBlank()) {
            matches.addAll(semanticMemoryRepository.searchUserMemories(
                    userId,
                    null,
                    status,
                    request.query(),
                    PageRequest.of(0, limit)));
        }
        return matches;
    }

    private Map<String, Integer> redactionSummary(List<SemanticMemory> rows) {
        Map<String, Integer> summary = new LinkedHashMap<>();
        for (SemanticMemory row : rows) {
            mergeCounts(summary, privacyService.scan(row.getContent()).counts());
            mergeCounts(summary, privacyService.scan(row.getEvidenceExcerpt()).counts());
            mergeCounts(summary, privacyService.scan(row.getMetadataJson()).counts());
        }
        return summary;
    }

    private void mergeCounts(Map<String, Integer> target, Map<String, Integer> source) {
        source.forEach((type, count) -> target.merge(type, count, Integer::sum));
    }

    private void assertSafeToStore(String content) {
        MemoryPrivacyService.MemoryPrivacyScanResult scan = privacyService.scan(content);
        if (!scan.safeToStore()) {
            throw new IllegalArgumentException("Memory content contains sensitive data: " + scan.counts().keySet());
        }
    }

    private String contentHash(SemanticMemory memory) {
        if (memory.getContentHash() != null && !memory.getContentHash().isBlank()) {
            return memory.getContentHash();
        }
        return privacyService.hashForAudit(memory.getContent());
    }

    private String proofEventJson(String requestId, Long memoryId, String contentHash) {
        return "{\"requestId\":\"" + escapeJson(requestId)
                + "\",\"memoryId\":" + memoryId
                + ",\"contentHash\":\"" + escapeJson(contentHash)
                + "\",\"status\":\"hard_deleted\"}";
    }

    private String deleteProofJson(String requestId,
                                   String userId,
                                   List<Long> ids,
                                   List<String> hashes,
                                   LocalDateTime requestedAt,
                                   LocalDateTime completedAt) {
        return "{\"requestId\":\"" + escapeJson(requestId)
                + "\",\"userId\":\"" + escapeJson(userId)
                + "\",\"deletedMemoryIds\":" + ids
                + ",\"contentHashes\":" + quotedArray(hashes)
                + ",\"requestedAt\":\"" + requestedAt
                + "\",\"completedAt\":\"" + completedAt
                + "\",\"status\":\"completed\"}";
    }

    private String quotedArray(List<String> values) {
        return values.stream()
                .map(value -> "\"" + escapeJson(value) + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(cursor));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return PAGE_SIZE;
        }
        return Math.max(1, Math.min(100, limit));
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeLower(status);
        return switch (normalized) {
            case "active", "disabled", "deleted", "retracted", "superseded" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported memory status: " + status);
        };
    }

    private String normalizeLower(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String escapeJson(String value) {
        return nullSafe(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
