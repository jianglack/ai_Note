package com.ainote.app.service;

import com.ainote.app.entity.MemoryEvent;
import com.ainote.app.entity.SemanticMemory;
import com.ainote.app.model.memory.MemoryForgetRequest;
import com.ainote.app.model.memory.MemoryForgetResponse;
import com.ainote.app.model.memory.MemoryListResponse;
import com.ainote.app.model.memory.MemoryResponse;
import com.ainote.app.model.memory.MemoryUpdateRequest;
import com.ainote.app.repository.MemoryEventRepository;
import com.ainote.app.repository.SemanticMemoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
@Transactional
public class MemoryControlService {

    private static final int PAGE_SIZE = 50;

    private final SemanticMemoryRepository semanticMemoryRepository;
    private final MemoryEventRepository memoryEventRepository;

    public MemoryControlService(SemanticMemoryRepository semanticMemoryRepository,
                                MemoryEventRepository memoryEventRepository) {
        this.semanticMemoryRepository = semanticMemoryRepository;
        this.memoryEventRepository = memoryEventRepository;
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

    public MemoryResponse updateMemory(String userId, Long id, MemoryUpdateRequest request) {
        SemanticMemory memory = semanticMemoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NoSuchElementException("Memory not found: " + id));
        String before = snapshot(memory);

        if (request.content() != null) {
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

    public void deleteMemory(String userId, Long id) {
        SemanticMemory memory = semanticMemoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NoSuchElementException("Memory not found: " + id));
        String before = snapshot(memory);
        memory.setStatus("deleted");
        SemanticMemory saved = semanticMemoryRepository.save(memory);
        recordEvent(userId, saved.getId(), "DELETED", "user", "user deleted memory", before, snapshot(saved));
    }

    public MemoryForgetResponse forgetMemories(String userId, MemoryForgetRequest request) {
        List<SemanticMemory> matches = new ArrayList<>();
        if (request.memoryIds() != null && !request.memoryIds().isEmpty()) {
            matches.addAll(semanticMemoryRepository.findByUserIdAndIdIn(userId, request.memoryIds()));
        } else if (request.query() != null && !request.query().isBlank()) {
            matches.addAll(semanticMemoryRepository.searchUserMemories(
                    userId,
                    null,
                    "active",
                    request.query(),
                    PageRequest.of(0, PAGE_SIZE)));
        }

        for (SemanticMemory memory : matches) {
            String before = snapshot(memory);
            memory.setStatus("deleted");
            SemanticMemory saved = semanticMemoryRepository.save(memory);
            recordEvent(userId, saved.getId(), "DELETED", "user", request.reason(), before, snapshot(saved));
        }
        return new MemoryForgetResponse(matches.size());
    }

    @Transactional(readOnly = true)
    public MemoryListResponse exportMemories(String userId) {
        List<MemoryResponse> items = semanticMemoryRepository.searchUserMemories(
                userId, null, null, null, PageRequest.of(0, 1000)).stream()
                .map(this::toResponse)
                .toList();
        return new MemoryListResponse(items, null);
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
        event.setReason(reason);
        event.setBeforeJson(beforeJson);
        event.setAfterJson(afterJson);
        memoryEventRepository.save(event);
    }

    private MemoryResponse toResponse(SemanticMemory memory) {
        return new MemoryResponse(
                memory.getId(),
                "semantic",
                memory.getMemoryType(),
                memory.getCategory(),
                memory.getContent(),
                memory.getConfidence(),
                memory.getSource(),
                memory.getScope(),
                memory.getStatus(),
                memory.getSourceTraceId(),
                memory.getSourceMessageIds(),
                memory.getSourceToolCallId(),
                memory.getEvidenceExcerpt(),
                memory.getLastAccessedAt(),
                memory.getAccessCount(),
                memory.getSupersedesId(),
                memory.getCreatedAt(),
                memory.getUpdatedAt());
    }

    private String snapshot(SemanticMemory memory) {
        return "{\"id\":" + memory.getId()
                + ",\"status\":\"" + nullSafe(memory.getStatus())
                + "\",\"memoryType\":\"" + nullSafe(memory.getMemoryType())
                + "\",\"content\":\"" + nullSafe(memory.getContent()).replace("\"", "\\\"")
                + "\"}";
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
}
