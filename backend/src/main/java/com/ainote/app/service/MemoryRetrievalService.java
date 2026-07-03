package com.ainote.app.service;

import com.ainote.app.entity.EpisodicMemory;
import com.ainote.app.entity.SemanticMemory;
import com.ainote.app.repository.EpisodicMemoryRepository;
import com.ainote.app.repository.SemanticMemoryRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MemoryRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(MemoryRetrievalService.class);
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.25;
    private static final int CANDIDATE_MULTIPLIER = 10;
    private static final double RECENCY_HALF_LIFE_DAYS = 30.0;

    private final EmbeddingModel embeddingModel;
    private final SemanticMemoryRepository semanticMemoryRepository;
    private final EpisodicMemoryRepository episodicMemoryRepository;

    public MemoryRetrievalService(EmbeddingModel embeddingModel,
                                  SemanticMemoryRepository semanticMemoryRepository,
                                  EpisodicMemoryRepository episodicMemoryRepository) {
        this.embeddingModel = embeddingModel;
        this.semanticMemoryRepository = semanticMemoryRepository;
        this.episodicMemoryRepository = episodicMemoryRepository;
    }

    public MemoryRetrievalResult retrieveForQuery(String userId,
                                                  String query,
                                                  int semanticLimit,
                                                  int episodicLimit) {
        if (userId == null || userId.isBlank()) {
            return MemoryRetrievalResult.empty();
        }
        if (query == null || query.isBlank()) {
            return fallback(userId, semanticLimit, episodicLimit, "blank_query");
        }

        String embedding;
        try {
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            embedding = embeddingToString(queryEmbedding);
        } catch (Exception e) {
            log.warn("memory_retrieval_event=embedding_failed user_id={} query_chars={}",
                    userId, query.length(), e);
            return fallback(userId, semanticLimit, episodicLimit, "embedding_failed");
        }

        List<SemanticMemory> semantic = retrieveSemantic(userId, embedding, semanticLimit);
        List<EpisodicMemory> episodic = retrieveEpisodic(userId, embedding, episodicLimit);

        if (semantic.isEmpty() && semanticLimit > 0) {
            semantic = semanticMemoryRepository.findRetrievalFallback(
                    userId, PageRequest.of(0, Math.max(semanticLimit, 1)));
        }
        if (episodic.isEmpty() && episodicLimit > 0) {
            episodic = episodicMemoryRepository.findRecentByUserId(
                    userId, PageRequest.of(0, Math.max(episodicLimit, 1)));
        }

        log.info("memory_retrieval_event=query_relevant_retrieved user_id={} semantic_count={} episodic_count={} threshold={}",
                userId, semantic.size(), episodic.size(), DEFAULT_SIMILARITY_THRESHOLD);
        return new MemoryRetrievalResult(semantic, episodic);
    }

    private List<SemanticMemory> retrieveSemantic(String userId, String embedding, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        int candidateLimit = Math.max(limit, limit * CANDIDATE_MULTIPLIER);
        List<SemanticMemoryRepository.MemorySimilarityView> matches =
                semanticMemoryRepository.findRelevantSemanticMatches(
                        userId, embedding, DEFAULT_SIMILARITY_THRESHOLD, candidateLimit);
        if (matches.isEmpty()) {
            return List.of();
        }

        List<Long> ids = matches.stream()
                .map(SemanticMemoryRepository.MemorySimilarityView::getId)
                .toList();
        Map<Long, SemanticMemory> byId = new HashMap<>();
        for (SemanticMemory memory : semanticMemoryRepository.findActiveByUserIdAndIdIn(userId, ids)) {
            byId.put(memory.getId(), memory);
        }

        Map<Long, Double> similarityById = new HashMap<>();
        for (SemanticMemoryRepository.MemorySimilarityView match : matches) {
            similarityById.put(match.getId(), safeDouble(match.getSemanticSimilarity(), 0));
        }

        return ids.stream()
                .map(byId::get)
                .filter(memory -> memory != null)
                .sorted(Comparator
                        .comparingDouble((SemanticMemory memory) -> scoreSemantic(
                                memory,
                                similarityById.getOrDefault(memory.getId(), 0.0)).total())
                        .reversed())
                .limit(limit)
                .toList();
    }

    private List<EpisodicMemory> retrieveEpisodic(String userId, String embedding, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        int candidateLimit = Math.max(limit, limit * CANDIDATE_MULTIPLIER);
        List<EpisodicMemoryRepository.MemorySimilarityView> matches =
                episodicMemoryRepository.findRelevantEpisodicMatches(
                        userId, embedding, DEFAULT_SIMILARITY_THRESHOLD, candidateLimit);
        if (matches.isEmpty()) {
            return List.of();
        }

        List<Long> ids = matches.stream()
                .map(EpisodicMemoryRepository.MemorySimilarityView::getId)
                .toList();
        Map<Long, EpisodicMemory> byId = new HashMap<>();
        for (EpisodicMemory memory : episodicMemoryRepository.findActiveByUserIdAndIdIn(userId, ids)) {
            byId.put(memory.getId(), memory);
        }

        return ids.stream()
                .map(byId::get)
                .filter(memory -> memory != null)
                .limit(limit)
                .toList();
    }

    private MemoryScore scoreSemantic(SemanticMemory memory, double semanticSimilarity) {
        return new MemoryScore(
                semanticSimilarity,
                safeDouble(memory.getConfidence(), 0.5),
                recencyDecay(memory),
                reinforcement(memory),
                scopePriority(memory.getScope())
        );
    }

    private double recencyDecay(SemanticMemory memory) {
        LocalDateTime reference = memory.getLastReinforcedAt();
        if (reference == null) {
            reference = memory.getUpdatedAt();
        }
        if (reference == null) {
            reference = memory.getCreatedAt();
        }
        if (reference == null) {
            return 0.5;
        }
        long days = Math.max(0, Duration.between(reference, LocalDateTime.now()).toDays());
        return Math.pow(0.5, days / RECENCY_HALF_LIFE_DAYS);
    }

    private double reinforcement(SemanticMemory memory) {
        int times = memory.getTimesReinforced() == null ? 1 : memory.getTimesReinforced();
        return Math.min(1.0, Math.log1p(Math.max(0, times)) / Math.log(11));
    }

    private double scopePriority(String scope) {
        if (scope == null || scope.isBlank()) {
            return 0.6;
        }
        return switch (scope) {
            case "user" -> 1.0;
            case "project" -> 0.9;
            case "note" -> 0.8;
            case "session" -> 0.6;
            default -> 0.5;
        };
    }

    private MemoryRetrievalResult fallback(String userId,
                                           int semanticLimit,
                                           int episodicLimit,
                                           String reason) {
        List<SemanticMemory> semantic = semanticLimit <= 0
                ? List.of()
                : semanticMemoryRepository.findRetrievalFallback(
                        userId, PageRequest.of(0, Math.max(semanticLimit, 1)));
        List<EpisodicMemory> episodic = episodicLimit <= 0
                ? List.of()
                : episodicMemoryRepository.findRecentByUserId(
                        userId, PageRequest.of(0, Math.max(episodicLimit, 1)));
        log.info("memory_retrieval_event=fallback user_id={} reason={} semantic_count={} episodic_count={}",
                userId, reason, semantic.size(), episodic.size());
        return new MemoryRetrievalResult(semantic, episodic);
    }

    private String embeddingToString(Embedding embedding) {
        float[] vector = embedding.vector();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    private double safeDouble(Double value, double defaultValue) {
        return value == null ? defaultValue : value;
    }

    public record MemoryRetrievalResult(List<SemanticMemory> semanticMemories,
                                        List<EpisodicMemory> episodicMemories) {
        public static MemoryRetrievalResult empty() {
            return new MemoryRetrievalResult(List.of(), List.of());
        }
    }
}
