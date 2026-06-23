package com.ainote.app.service;

import com.ainote.app.entity.Note;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 重排序服务
 * 对检索结果进行重排序，提升检索准确度
 */
@Service
public class RerankingService {

    private static final Logger log = LoggerFactory.getLogger(RerankingService.class);

    @Value("${app.cohere.api-key:}")
    private String cohereApiKey;

    @Value("${app.cohere.base-url:https://api.cohere.ai/v1}")
    private String cohereBaseUrl;

    @Value("${app.rag.reranking.enabled:true}")
    private boolean enabled;

    @Value("${app.rag.reranking.use-cohere:false}")
    private boolean useCohere;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RerankingService(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * 对检索结果进行重排序
     * 
     * @param query 用户查询
     * @param candidates 候选笔记列表
     * @param topN 返回前 N 个结果
     * @return 重排序后的笔记列表
     */
    public List<Note> rerank(String query, List<Note> candidates, int topN) {
        if (!enabled || candidates == null || candidates.isEmpty()) {
            log.debug("Reranking disabled or no candidates, returning original list");
            return candidates;
        }

        if (useCohere && cohereApiKey != null && !cohereApiKey.isEmpty()) {
            return cohereRerank(query, candidates, topN);
        } else {
            return simpleRerank(query, candidates, topN);
        }
    }

    /**
     * 使用 Cohere API 进行重排序
     */
    private List<Note> cohereRerank(String query, List<Note> candidates, int topN) {
        try {
            log.info("Using Cohere reranking for {} candidates", candidates.size());

            List<String> documents = candidates.stream()
                .map(note -> note.getTitle() + "\n" + 
                     (note.getContent() != null ? note.getContent() : ""))
                .collect(Collectors.toList());

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "rerank-v3.5");
            requestBody.put("query", query);
            requestBody.put("documents", documents);
            requestBody.put("top_n", Math.min(topN, candidates.size()));

            String requestJson = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cohereBaseUrl + "/rerank"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cohereApiKey)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Cohere API error: {} - {}", response.statusCode(), response.body());
                return simpleRerank(query, candidates, topN);
            }

            JsonNode responseJson = objectMapper.readTree(response.body());
            JsonNode results = responseJson.path("results");

            List<Note> rerankedNotes = new ArrayList<>();
            for (JsonNode result : results) {
                int index = result.path("index").asInt();
                double relevanceScore = result.path("relevance_score").asDouble();
                
                if (index < candidates.size()) {
                    Note note = candidates.get(index);
                    rerankedNotes.add(note);
                    log.debug("Reranked note '{}' with score {}", 
                             note.getTitle(), relevanceScore);
                }
            }

            log.info("Cohere reranking completed: {} -> {} results", 
                     candidates.size(), rerankedNotes.size());

            return rerankedNotes;

        } catch (Exception e) {
            log.error("Cohere reranking failed, falling back to simple reranking", e);
            return simpleRerank(query, candidates, topN);
        }
    }

    /**
     * 简单的关键词匹配重排序
     */
    private List<Note> simpleRerank(String query, List<Note> candidates, int topN) {
        log.info("Using simple reranking for {} candidates", candidates.size());

        String[] keywords = query.toLowerCase().split("\\s+");

        List<NoteWithScore> scoredNotes = candidates.stream()
            .map(note -> {
                double score = calculateKeywordScore(keywords, note);
                return new NoteWithScore(note, score);
            })
            .sorted(Comparator.comparingDouble(NoteWithScore::score).reversed())
            .limit(topN)
            .collect(Collectors.toList());

        List<Note> rerankedNotes = scoredNotes.stream()
            .map(NoteWithScore::note)
            .collect(Collectors.toList());

        log.info("Simple reranking completed: {} -> {} results", 
                 candidates.size(), rerankedNotes.size());

        return rerankedNotes;
    }

    /**
     * 计算关键词匹配分数
     */
    private double calculateKeywordScore(String[] keywords, Note note) {
        String title = note.getTitle() != null ? note.getTitle().toLowerCase() : "";
        String content = note.getContent() != null ? note.getContent().toLowerCase() : "";
        String combinedText = title + " " + content;

        double score = 0.0;

        for (String keyword : keywords) {
            if (keyword.isEmpty()) continue;

            if (title.contains(keyword)) {
                score += 3.0;
            }

            if (content.contains(keyword)) {
                long count = countOccurrences(content, keyword);
                score += count * 1.0;
            }

            if (note.getTags() != null) {
                boolean hasTag = note.getTags().stream()
                    .anyMatch(tag -> tag.getName().toLowerCase().contains(keyword));
                if (hasTag) {
                    score += 2.0;
                }
            }
        }

        return score;
    }

    /**
     * 统计关键词出现次数
     */
    private long countOccurrences(String text, String keyword) {
        long count = 0;
        int index = 0;
        while ((index = text.indexOf(keyword, index)) != -1) {
            count++;
            index += keyword.length();
        }
        return count;
    }

    /**
     * 获取重排序统计信息
     */
    public RerankingStats getStats(List<Note> original, List<Note> reranked, String method) {
        return new RerankingStats(
            original.size(),
            reranked.size(),
            method,
            enabled
        );
    }

    private record NoteWithScore(Note note, double score) {}

    public record RerankingStats(
        int originalCount,
        int rerankedCount,
        String method,
        boolean enabled
    ) {
        public double reductionPercentage() {
            return originalCount > 0 
                ? (1 - (double) rerankedCount / originalCount) * 100 
                : 0.0;
        }
    }
}
