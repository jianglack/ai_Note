package com.ainote.app.service;

import com.ainote.app.entity.MemoryEvent;
import com.ainote.app.entity.SemanticMemory;
import com.ainote.app.repository.MemoryEventRepository;
import com.ainote.app.repository.SemanticMemoryRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MemoryWriteService {

    private static final Logger log = LoggerFactory.getLogger(MemoryWriteService.class);

    private final SemanticMemoryRepository semanticMemoryRepository;
    private final MemoryEventRepository memoryEventRepository;
    private final EmbeddingModel embeddingModel;

    public MemoryWriteService(SemanticMemoryRepository semanticMemoryRepository,
                              MemoryEventRepository memoryEventRepository,
                              EmbeddingModel embeddingModel) {
        this.semanticMemoryRepository = semanticMemoryRepository;
        this.memoryEventRepository = memoryEventRepository;
        this.embeddingModel = embeddingModel;
    }

    @Transactional
    public MemoryWriteResult writeCandidates(String userId,
                                             List<MemoryCandidateExtractor.MemoryCandidate> candidates,
                                             String reason) {
        if (userId == null || userId.isBlank() || candidates == null || candidates.isEmpty()) {
            return new MemoryWriteResult(0, 0, 0, 0);
        }

        int created = 0;
        int reinforced = 0;
        int superseded = 0;
        int skipped = 0;
        for (MemoryCandidateExtractor.MemoryCandidate candidate : candidates) {
            if (candidate == null || candidate.content() == null || candidate.content().isBlank()) {
                skipped++;
                continue;
            }

            List<SemanticMemory> exact = semanticMemoryRepository.findByUserIdAndContent(userId, candidate.content());
            if (!exact.isEmpty()) {
                SemanticMemory memory = exact.get(0);
                reinforce(memory, candidate);
                semanticMemoryRepository.save(memory);
                recordEvent(userId, memory.getId(), "REINFORCED", "assistant", reason, null, snapshot(memory));
                reinforced++;
                continue;
            }

            Long supersedesId = null;
            if (candidate.correction()) {
                for (SemanticMemory old : semanticMemoryRepository.findByUserIdAndCategory(userId, candidate.category())) {
                    if (isActive(old)) {
                        String before = snapshot(old);
                        old.setStatus("superseded");
                        semanticMemoryRepository.save(old);
                        recordEvent(userId, old.getId(), "SUPERSEDED", "assistant", reason, before, snapshot(old));
                        if (supersedesId == null) {
                            supersedesId = old.getId();
                        }
                        superseded++;
                    }
                }
            }

            SemanticMemory memory = toSemanticMemory(userId, candidate, supersedesId);
            SemanticMemory saved = semanticMemoryRepository.save(memory);
            persistEmbedding(saved);
            recordEvent(userId, saved.getId(), "CREATED", "assistant", reason, null, snapshot(saved));
            created++;
        }

        log.info("memory_write_event=completed user_id={} created={} reinforced={} superseded={} skipped={} reason={}",
                userId, created, reinforced, superseded, skipped, reason);
        return new MemoryWriteResult(created, reinforced, superseded, skipped);
    }

    private SemanticMemory toSemanticMemory(String userId,
                                            MemoryCandidateExtractor.MemoryCandidate candidate,
                                            Long supersedesId) {
        SemanticMemory memory = new SemanticMemory();
        memory.setUserId(userId);
        memory.setCategory(candidate.category());
        memory.setMemoryType(candidate.memoryType());
        memory.setContent(candidate.content());
        memory.setConfidence(candidate.confidence());
        memory.setSource("policy_extracted");
        memory.setScope(candidate.scope());
        memory.setStatus("active");
        memory.setEvidenceExcerpt(candidate.evidenceExcerpt());
        memory.setSupersedesId(supersedesId);
        memory.setLastReinforcedAt(LocalDateTime.now());
        memory.setTimesReinforced(1);
        memory.setDecayScore(candidate.confidence());
        return memory;
    }

    private void reinforce(SemanticMemory memory, MemoryCandidateExtractor.MemoryCandidate candidate) {
        int times = memory.getTimesReinforced() == null ? 1 : memory.getTimesReinforced();
        memory.setTimesReinforced(times + 1);
        memory.setLastReinforcedAt(LocalDateTime.now());
        if (memory.getConfidence() == null || candidate.confidence() > memory.getConfidence()) {
            memory.setConfidence(candidate.confidence());
        }
        memory.setEvidenceExcerpt(candidate.evidenceExcerpt());
    }

    private boolean isActive(SemanticMemory memory) {
        return memory != null && (memory.getStatus() == null || "active".equals(memory.getStatus()));
    }

    private void persistEmbedding(SemanticMemory memory) {
        if (memory.getId() == null) {
            return;
        }
        try {
            Embedding embedding = embeddingModel.embed(memory.getContent()).content();
            semanticMemoryRepository.updateEmbedding(memory.getId(), embeddingToString(embedding.vector()));
        } catch (Exception e) {
            log.warn("memory_write_event=embedding_failed user_id={} memory_id={} error={}",
                    memory.getUserId(), memory.getId(), e.getClass().getSimpleName());
        }
    }

    private String embeddingToString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
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

    private String snapshot(SemanticMemory memory) {
        return "{\"id\":" + memory.getId()
                + ",\"status\":\"" + safe(memory.getStatus())
                + "\",\"category\":\"" + safe(memory.getCategory())
                + "\",\"content\":\"" + safe(memory.getContent()).replace("\"", "\\\"")
                + "\"}";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record MemoryWriteResult(int created, int reinforced, int superseded, int skipped) {
    }
}
