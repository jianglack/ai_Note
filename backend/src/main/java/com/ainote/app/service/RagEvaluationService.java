package com.ainote.app.service;

import com.ainote.app.repository.NoteRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 离线评估服务
 * 基于评估数据集计算 Recall@K, MRR, NDCG 等指标
 *
 * 评估数据集格式：List<EvalCase>，每个 case 包含 query + 期望命中的 noteIds
 */
@Service
public class RagEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(RagEvaluationService.class);

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ResilientLlmService resilientLlmService;
    private final NoteRepository noteRepository;

    public RagEvaluationService(EmbeddingStore<TextSegment> embeddingStore,
                                 ResilientLlmService resilientLlmService,
                                 NoteRepository noteRepository) {
        this.embeddingStore = embeddingStore;
        this.resilientLlmService = resilientLlmService;
        this.noteRepository = noteRepository;
    }

    /**
     * 评估用例
     */
    public static class EvalCase {
        public String query;
        public List<String> expectedNoteIds;  // 期望命中的笔记 ID（有序，越前越相关）

        public EvalCase() {}
        public EvalCase(String query, List<String> expectedNoteIds) {
            this.query = query;
            this.expectedNoteIds = expectedNoteIds;
        }
    }

    /**
     * 单条评估结果
     */
    public static class EvalResult {
        public String query;
        public List<String> expectedIds;
        public List<String> retrievedIds;
        public List<Double> retrievedScores;
        public double recallAtK;
        public double mrr;
        public double ndcg;
    }

    /**
     * 聚合评估报告
     */
    public static class EvalReport {
        public int totalCases;
        public double avgRecallAtK;
        public double avgMrr;
        public double avgNdcg;
        public int k;
        public double minScore;
        public List<EvalResult> details;
    }

    /**
     * 执行离线评估
     *
     * @param cases    评估数据集
     * @param k        Recall@K 的 K 值
     * @param minScore 最小相似度阈值
     * @param userId   限定用户范围
     * @return 评估报告
     */
    public EvalReport evaluate(List<EvalCase> cases, int k, double minScore, String userId) {
        log.info("Starting RAG evaluation: {} cases, k={}, minScore={}", cases.size(), k, minScore);

        List<EvalResult> results = new ArrayList<>();

        for (EvalCase evalCase : cases) {
            EvalResult result = evaluateSingle(evalCase, k, minScore, userId);
            results.add(result);
        }

        // 聚合
        EvalReport report = new EvalReport();
        report.totalCases = cases.size();
        report.k = k;
        report.minScore = minScore;
        report.details = results;

        if (!results.isEmpty()) {
            report.avgRecallAtK = results.stream().mapToDouble(r -> r.recallAtK).average().orElse(0);
            report.avgMrr = results.stream().mapToDouble(r -> r.mrr).average().orElse(0);
            report.avgNdcg = results.stream().mapToDouble(r -> r.ndcg).average().orElse(0);
        }

        log.info("RAG evaluation complete: Recall@{}={}, MRR={}, NDCG={}",
                k, report.avgRecallAtK, report.avgMrr, report.avgNdcg);

        return report;
    }

    private EvalResult evaluateSingle(EvalCase evalCase, int k, double minScore, String userId) {
        EvalResult result = new EvalResult();
        result.query = evalCase.query;
        result.expectedIds = evalCase.expectedNoteIds;

        try {
            // 生成查询嵌入
            TextSegment querySegment = TextSegment.from(evalCase.query);
            Response<Embedding> response = resilientLlmService.embed(querySegment);
            if (response == null) {
                result.retrievedIds = List.of();
                result.retrievedScores = List.of();
                return result;
            }

            // 搜索
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(response.content())
                    .maxResults(k * 3)
                    .minScore(minScore)
                    .build();

            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(request);

            // 提取有序的 noteId + score（去重，保留最高分）
            Map<String, Double> noteScores = new LinkedHashMap<>();
            for (EmbeddingMatch<TextSegment> match : searchResult.matches()) {
                String noteId = match.embedded().metadata().getString("noteId");
                if (noteId != null) {
                    noteScores.merge(noteId, match.score(), Math::max);
                }
            }

            // 过滤用户范围
            Set<String> userNoteIds = noteRepository.findAllById(noteScores.keySet()).stream()
                    .filter(n -> n.getUser().getId().equals(userId) && n.getDeletedAt() == null)
                    .map(n -> n.getId())
                    .collect(Collectors.toSet());

            List<Map.Entry<String, Double>> filtered = noteScores.entrySet().stream()
                    .filter(e -> userNoteIds.contains(e.getKey()))
                    .limit(k)
                    .collect(Collectors.toList());

            result.retrievedIds = filtered.stream().map(Map.Entry::getKey).collect(Collectors.toList());
            result.retrievedScores = filtered.stream().map(Map.Entry::getValue).collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("Evaluation failed for query '{}': {}", evalCase.query, e.getMessage());
            result.retrievedIds = List.of();
            result.retrievedScores = List.of();
        }

        // 计算指标
        Set<String> expectedSet = new HashSet<>(evalCase.expectedNoteIds);

        // Recall@K
        long hits = result.retrievedIds.stream().filter(expectedSet::contains).count();
        result.recallAtK = expectedSet.isEmpty() ? 0 : (double) hits / expectedSet.size();

        // MRR (Mean Reciprocal Rank)
        result.mrr = 0;
        for (int i = 0; i < result.retrievedIds.size(); i++) {
            if (expectedSet.contains(result.retrievedIds.get(i))) {
                result.mrr = 1.0 / (i + 1);
                break;
            }
        }

        // NDCG
        result.ndcg = calculateNdcg(result.retrievedIds, evalCase.expectedNoteIds, k);

        return result;
    }

    /**
     * 计算 NDCG (Normalized Discounted Cumulative Gain)
     * relevance: 如果在 expectedIds 中，rel = 1/rank_in_expected（越靠前越相关）
     */
    private double calculateNdcg(List<String> retrieved, List<String> expected, int k) {
        if (expected.isEmpty()) return 0;

        // 构建 relevance map：expected 中排名越前的 rel 越高
        Map<String, Double> relevanceMap = new HashMap<>();
        for (int i = 0; i < expected.size(); i++) {
            relevanceMap.put(expected.get(i), (double) (expected.size() - i));
        }

        // DCG
        double dcg = 0;
        for (int i = 0; i < Math.min(retrieved.size(), k); i++) {
            double rel = relevanceMap.getOrDefault(retrieved.get(i), 0.0);
            dcg += rel / (Math.log(i + 2) / Math.log(2)); // log2(i+2)
        }

        // Ideal DCG
        List<Double> idealRels = new ArrayList<>(relevanceMap.values());
        idealRels.sort(Collections.reverseOrder());
        double idcg = 0;
        for (int i = 0; i < Math.min(idealRels.size(), k); i++) {
            idcg += idealRels.get(i) / (Math.log(i + 2) / Math.log(2));
        }

        return idcg == 0 ? 0 : dcg / idcg;
    }
}
