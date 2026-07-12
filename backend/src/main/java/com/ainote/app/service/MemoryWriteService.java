package com.ainote.app.service;

import com.ainote.app.entity.MemoryEvent;
import com.ainote.app.entity.SemanticMemory;
import com.ainote.app.repository.MemoryEventRepository;
import com.ainote.app.repository.SemanticMemoryRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class MemoryWriteService {

    private static final Logger log = LoggerFactory.getLogger(MemoryWriteService.class);

    private final SemanticMemoryRepository semanticMemoryRepository;
    private final MemoryEventRepository memoryEventRepository;
    private final EmbeddingModel embeddingModel;
    private final MemoryPrivacyService privacyService;
    private final MemoryMetricsService memoryMetricsService;

    public MemoryWriteService(SemanticMemoryRepository semanticMemoryRepository,
                              MemoryEventRepository memoryEventRepository,
                              EmbeddingModel embeddingModel) {
        this(semanticMemoryRepository, memoryEventRepository, embeddingModel, new MemoryPrivacyService(), MemoryMetricsService.noop());
    }

    public MemoryWriteService(SemanticMemoryRepository semanticMemoryRepository,
                              MemoryEventRepository memoryEventRepository,
                              EmbeddingModel embeddingModel,
                              MemoryPrivacyService privacyService) {
        this(semanticMemoryRepository, memoryEventRepository, embeddingModel, privacyService, MemoryMetricsService.noop());
    }

    @Autowired
    public MemoryWriteService(SemanticMemoryRepository semanticMemoryRepository,
                              MemoryEventRepository memoryEventRepository,
                              EmbeddingModel embeddingModel,
                              MemoryPrivacyService privacyService,
                              MemoryMetricsService memoryMetricsService) {
        this.semanticMemoryRepository = semanticMemoryRepository;
        this.memoryEventRepository = memoryEventRepository;
        this.embeddingModel = embeddingModel;
        this.privacyService = privacyService == null ? new MemoryPrivacyService() : privacyService;
        this.memoryMetricsService = memoryMetricsService == null ? MemoryMetricsService.noop() : memoryMetricsService;
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
            MemoryPrivacyService.MemoryPrivacyScanResult privacyScan = privacyService.scan(candidate.content());
            if (!privacyScan.safeToStore()) {
                recordEvent(userId, null, "CAPTURE_REJECTED_PRIVACY", "system", reason, null,
                        "{\"status\":\"rejected\",\"privacyFindings\":" + jsonObject(privacyScan.counts()) + "}",
                        traceIdFor(userId, candidate));
                skipped++;
                continue;
            }

            List<SemanticMemory> exact = semanticMemoryRepository.findByUserIdAndContent(userId, candidate.content());
            if (!exact.isEmpty()) {
                SemanticMemory memory = exact.get(0);
                reinforce(memory, candidate);
                applyGovernanceMetadata(memory, candidate, reason);
                semanticMemoryRepository.save(memory);
                recordEvent(userId, memory.getId(), "REINFORCED", "assistant", reason, null,
                        snapshot(memory), memory.getSourceTraceId());
                reinforced++;
                continue;
            }

            Long supersedesId = null;
            String candidateTraceId = traceIdFor(userId, candidate);
            if (candidate.correction()) {
                for (SemanticMemory old : semanticMemoryRepository.findByUserIdAndCategory(userId, candidate.category())) {
                    if (isActive(old)) {
                        String before = snapshot(old);
                        old.setStatus("superseded");
                        semanticMemoryRepository.save(old);
                        recordEvent(userId, old.getId(), "SUPERSEDED", "assistant", reason, before,
                                snapshot(old), candidateTraceId);
                        if (supersedesId == null) {
                            supersedesId = old.getId();
                        }
                        superseded++;
                    }
                }
            }

            SemanticMemory memory = toSemanticMemory(userId, candidate, supersedesId);
            applyGovernanceMetadata(memory, candidate, reason);
            SemanticMemory saved = semanticMemoryRepository.save(memory);
            persistEmbedding(saved);
            recordEvent(userId, saved.getId(), "CREATED", "assistant", reason, null,
                    snapshot(saved), saved.getSourceTraceId());
            created++;
        }

        log.info("memory_write_event=completed user_id={} created={} reinforced={} superseded={} skipped={} reason={}",
                userId, created, reinforced, superseded, skipped, reason);
        MemoryWriteResult result = new MemoryWriteResult(created, reinforced, superseded, skipped);
        memoryMetricsService.recordWriteResult(result, reason);
        return result;
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

    public void recordCaptureFailure(String userId, String reason, Throwable error, String traceId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        String resolvedTraceId = traceId == null || traceId.isBlank()
                ? "memory-capture-" + sha256(userId + "\u001f" + safe(reason) + "\u001f" + errorClass(error))
                        .substring(0, 24)
                : traceId;
        MemoryEvent event = new MemoryEvent();
        event.setUserId(userId);
        event.setEventType("CAPTURE_FAILED");
        event.setActor("system");
        event.setReason(reason);
        event.setTraceId(resolvedTraceId);
        event.setAfterJson("{\"error_class\":\"" + jsonEscape(errorClass(error))
                + "\",\"message\":\"" + jsonEscape(errorMessage(error)) + "\"}");
        memoryEventRepository.save(event);
    }

    public void recordCaptureDecision(String userId,
                                      String status,
                                      String reason,
                                      MemoryCapturePolicy.CaptureDecision decision,
                                      String userMessage) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        String normalizedStatus = safe(status).isBlank() ? "unknown" : status;
        String eventType = switch (normalizedStatus) {
            case "denied", "rejected" -> "CAPTURE_REJECTED";
            case "skipped" -> "CAPTURE_SKIPPED";
            case "succeeded", "allowed" -> "CAPTURE_ALLOWED";
            default -> "CAPTURE_DECISION";
        };
        MemoryPrivacyService.MemoryPrivacyScanResult privacyScan = privacyService.scan(userMessage);
        String resolvedReason = safe(reason).isBlank()
                && decision != null
                ? decision.reason()
                : reason;
        String afterJson = "{\"status\":\"" + jsonEscape(normalizedStatus)
                + "\",\"reason\":\"" + jsonEscape(resolvedReason)
                + "\",\"decisionType\":\"" + jsonEscape(decision == null ? "" : decision.type().name())
                + "\",\"allowed\":" + (decision != null && decision.allowed())
                + ",\"matchedSignals\":" + jsonArray(decision == null ? List.of() : decision.matchedSignals())
                + ",\"privacyFindings\":" + jsonObject(privacyScan.counts())
                + ",\"userMessageHash\":\"sha256:" + sha256(userMessage)
                + "\",\"userMessagePreview\":\"" + jsonEscape(truncate(privacyService.redact(userMessage), 160))
                + "\"}";
        recordEvent(userId, null, eventType, "system", resolvedReason, null, afterJson,
                "memory-capture-" + sha256(safe(userId)
                        + "\u001f" + safe(userMessage)
                        + "\u001f" + safe(resolvedReason)).substring(0, 24));
    }

    private void applyGovernanceMetadata(SemanticMemory memory,
                                         MemoryCandidateExtractor.MemoryCandidate candidate,
                                         String reason) {
        String policyReason = candidate.policyReason().isBlank() ? reason : candidate.policyReason();
        memory.setContentHash("sha256:" + sha256(candidate.content()));
        memory.setSourceTraceId(traceIdFor(memory.getUserId(), candidate));
        memory.setSourceMessageIds("{\"user_message_hash\":\"sha256:" + sha256(candidate.evidenceExcerpt()) + "\"}");
        memory.setMetadataJson("{\"capture_reason\":\"" + jsonEscape(reason)
                + "\",\"policy_reason\":\"" + jsonEscape(policyReason)
                + "\",\"decision_type\":\"" + jsonEscape(candidate.decisionType())
                + "\",\"candidate_confidence\":" + candidate.confidence()
                + ",\"policy_signals\":" + jsonArray(candidate.policySignals())
                + ",\"policy_source\":\"memory_write_service\"}");
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
                             String afterJson,
                             String traceId) {
        MemoryEvent event = new MemoryEvent();
        event.setUserId(userId);
        event.setMemoryId(memoryId);
        event.setEventType(eventType);
        event.setActor(actor);
        event.setReason(privacyService.redact(reason));
        event.setBeforeJson(beforeJson);
        event.setAfterJson(afterJson);
        event.setTraceId(traceId);
        memoryEventRepository.save(event);
    }

    private String snapshot(SemanticMemory memory) {
        return "{\"id\":" + memory.getId()
                + ",\"status\":\"" + safe(memory.getStatus())
                + "\",\"category\":\"" + safe(memory.getCategory())
                + "\",\"contentHash\":\"" + safe(memory.getContentHash())
                + "\",\"sourceTraceId\":\"" + safe(memory.getSourceTraceId())
                + "\",\"metadata\":" + jsonObjectOrEmpty(memory.getMetadataJson())
                + ",\"content\":\"" + jsonEscape(privacyService.redact(memory.getContent()))
                + "\"}";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String traceIdFor(String userId, MemoryCandidateExtractor.MemoryCandidate candidate) {
        return "memory-capture-" + sha256(safe(userId)
                + "\u001f" + safe(candidate.evidenceExcerpt())
                + "\u001f" + safe(candidate.content())).substring(0, 24);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(safe(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String jsonEscape(String value) {
        return safe(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String jsonArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"').append(jsonEscape(values.get(i))).append('"');
        }
        return builder.append(']').toString();
    }

    private String jsonObject(Map<String, Integer> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        int index = 0;
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (index++ > 0) {
                builder.append(',');
            }
            builder.append('"').append(jsonEscape(entry.getKey())).append('"')
                    .append(':')
                    .append(entry.getValue());
        }
        return builder.append('}').toString();
    }

    private String jsonObjectOrEmpty(String value) {
        if (value == null || value.isBlank()) {
            return "{}";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        return "{\"raw\":\"" + jsonEscape(trimmed) + "\"}";
    }

    private String errorClass(Throwable error) {
        return error == null ? "Unknown" : error.getClass().getSimpleName();
    }

    private String errorMessage(Throwable error) {
        return error == null || error.getMessage() == null ? "" : error.getMessage();
    }

    private String truncate(String value, int maxChars) {
        String safeValue = safe(value);
        if (safeValue.length() <= maxChars) {
            return safeValue;
        }
        return safeValue.substring(0, Math.max(0, maxChars - 1)).trim() + "…";
    }

    public record MemoryWriteResult(int created, int reinforced, int superseded, int skipped) {
    }
}
