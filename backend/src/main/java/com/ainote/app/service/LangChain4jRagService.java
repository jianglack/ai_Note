package com.ainote.app.service;

import com.ainote.app.chunking.StructureAwareDocumentSplitter;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import com.ainote.app.entity.NoteMedia;
import com.ainote.app.model.Note;
import com.ainote.app.repository.NoteMediaRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * LangChain4j RAG 服务
 * 使用 LangChain4j 框架实现文档分块、嵌入生成和语义搜索
 */
@Service
public class LangChain4jRagService {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jRagService.class);
    private static final int MAX_REWRITE_VARIANTS = 3;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentSplitter documentSplitter;
    private final NoteRepository noteRepository;
    private final SecurityUtils securityUtils;
    private final int ragMaxResults;
    private final double ragMinScore;

    @Value("${app.rag.reranking.max-candidates:20}")
    private int rerankMaxCandidates = 20;

    // 可选的 Reranker（如果配置了 Cohere API Key）
    private final ScoringModel scoringModel;

    // Query Rewriting 服务
    private final QueryRewritingService queryRewritingService;

    // JDBC 用于删除旧 embedding
    private final JdbcTemplate jdbcTemplate;

    // 熔断保护的 LLM 调用服务
    private final ResilientLlmService resilientLlmService;

    // 多模态内容仓库
    private final NoteMediaRepository noteMediaRepository;

    public LangChain4jRagService(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            NoteRepository noteRepository,
            SecurityUtils securityUtils,
            @Autowired(required = false) ScoringModel scoringModel,
            QueryRewritingService queryRewritingService,
            JdbcTemplate jdbcTemplate,
            StructureAwareDocumentSplitter structureAwareSplitter,
            ResilientLlmService resilientLlmService,
            NoteMediaRepository noteMediaRepository,
            @Value("${app.rag.max-results:10}") int ragMaxResults,
            @Value("${app.rag.min-score:0.5}") double ragMinScore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.noteRepository = noteRepository;
        this.securityUtils = securityUtils;
        this.scoringModel = scoringModel;
        this.queryRewritingService = queryRewritingService;
        this.jdbcTemplate = jdbcTemplate;
        this.resilientLlmService = resilientLlmService;
        this.noteMediaRepository = noteMediaRepository;
        this.ragMaxResults = ragMaxResults;
        this.ragMinScore = ragMinScore;

        // 使用结构感知分块器 - 智能识别文档结构并保留层级信息
        this.documentSplitter = structureAwareSplitter;
        log.info("LangChain4jRagService initialized with StructureAwareDocumentSplitter, ragMaxResults: {}, ragMinScore: {}, reranker: {}, query-rewriting: {}",
                ragMaxResults,
                ragMinScore,
                scoringModel != null ? "enabled" : "disabled",
                queryRewritingService != null ? "enabled" : "disabled");
    }

    private ContentRetriever buildContentRetriever(String userId) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(ragMaxResults)
                .minScore(ragMinScore)
                .filter(RagFilterFactory.userFilter(userId))
                .build();
    }

    private List<String> limitRewriteVariants(List<String> queries, String fallbackQuery) {
        List<String> limited = queries == null ? new ArrayList<>() : queries.stream()
                .filter(q -> q != null && !q.isBlank())
                .map(String::trim)
                .distinct()
                .limit(MAX_REWRITE_VARIANTS)
                .collect(Collectors.toList());
        if (limited.isEmpty() && fallbackQuery != null && !fallbackQuery.isBlank()) {
            limited.add(fallbackQuery);
        }
        return limited;
    }

    private List<Content> retrieveContents(ContentRetriever retriever, List<String> queries) {
        return queries.parallelStream()
                .map(query -> retriever.retrieve(Query.from(query)))
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * 清理 HTML 标签
     */
    private String cleanHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        return html
                .replaceAll("<br\\s*/?>", "\n")
                .replaceAll("</p>", "\n")
                .replaceAll("</div>", "\n")
                .replaceAll("</li>", "\n")
                .replaceAll("</h[1-6]>", "\n")
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    /**
     * 异步为笔记生成嵌入
     * 使用 LangChain4j DocumentSplitter 进行智能分块
     */
    @Async("taskExecutor")
    public CompletableFuture<Void> generateEmbeddingAsync(String noteId) {
        log.info("=== LangChain4j: Generating embedding for note: {} ===", noteId);
        try {
            Optional<com.ainote.app.entity.Note> noteOpt = noteRepository.findById(noteId);
            if (noteOpt.isEmpty()) {
                log.warn("Note not found: {}", noteId);
                return CompletableFuture.completedFuture(null);
            }

            com.ainote.app.entity.Note note = noteOpt.get();
            String userId = note.getUser().getId();

            // 清理内容
            String cleanContent = cleanHtml(note.getContent());
            String fullText = note.getTitle() + "\n\n" + cleanContent;

            // 追加多模态内容（OCR 文本 + 表格 Markdown）
            List<NoteMedia> mediaList = noteMediaRepository.findByNoteId(noteId);
            StringBuilder mediaText = new StringBuilder();
            for (NoteMedia m : mediaList) {
                if ("image".equals(m.getMediaType()) && m.getOcrText() != null && !m.getOcrText().isEmpty()) {
                    mediaText.append("\n\n[Image OCR: ").append(m.getOcrText()).append("]");
                }
                if ("table".equals(m.getMediaType()) && m.getTableMarkdown() != null && !m.getTableMarkdown().isEmpty()) {
                    mediaText.append("\n\n[Table: ").append(m.getTableMarkdown()).append("]");
                }
            }
            fullText = fullText + mediaText.toString();

            if (fullText.trim().isEmpty()) {
                log.warn("Note content is empty: {}", noteId);
                return CompletableFuture.completedFuture(null);
            }

            // 删除该笔记的旧嵌入
            // LangChain4j 的 EmbeddingStore 没有按 metadata 删除的原生支持
            // 需要通过自定义逻辑或直接操作数据库
            deleteEmbeddingsForNote(noteId);

            // 创建文档并分块
            Metadata metadata = Metadata.from("noteId", noteId)
                    .put("userId", userId)
                    .put("title", note.getTitle());

            Document document = Document.from(fullText, metadata);
            List<TextSegment> segments = documentSplitter.split(document);

            log.info("Note {} split into {} segments", noteId, segments.size());

            // 为每个段落生成嵌入并存储
            for (int i = 0; i < segments.size(); i++) {
                TextSegment segment = segments.get(i);

                // 添加分块索引到 metadata
                Metadata segmentMetadata = segment.metadata()
                        .put("chunkIndex", String.valueOf(i));
                TextSegment enrichedSegment = TextSegment.from(segment.text(), segmentMetadata);

                // 生成嵌入（通过熔断器保护）
                Response<Embedding> response = resilientLlmService.embed(enrichedSegment);
                if (response == null) {
                    log.warn("Embedding model unavailable, skipping segment {} for note {}", i, noteId);
                    continue;
                }
                Embedding embedding = response.content();

                // 存储到向量数据库
                embeddingStore.add(embedding, enrichedSegment);
                log.debug("Stored embedding for note {} segment {}", noteId, i);
            }

            log.info("=== LangChain4j: Completed embedding for note: {} ({} segments) ===",
                    noteId, segments.size());
            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            log.error("Error generating embedding for note {}: {}", noteId, e.getMessage(), e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * 删除笔记的所有嵌入
     * LangChain4j 的 PgVectorEmbeddingStore 将 metadata 存储在 JSON 列中
     */
    private void deleteEmbeddingsForNote(String noteId) {
        try {
            // langchain4j_embeddings 表的 metadata 列是 JSON 格式
            // 使用 JSONB 操作符查询并删除
            int deleted = jdbcTemplate.update(
                "DELETE FROM langchain4j_embeddings WHERE metadata->>'noteId' = ?",
                noteId
            );
            if (deleted > 0) {
                log.info("Deleted {} old embeddings for note: {}", deleted, noteId);
            }
        } catch (Exception e) {
            log.warn("Failed to delete old embeddings for note {}: {}", noteId, e.getMessage());
        }
    }

    /**
     * 语义搜索笔记
     * 使用 LangChain4j ContentRetriever，可选 Reranker 重排序和 Query Rewriting
     */
    @Transactional(readOnly = true)
    public List<Note> searchSimilar(String query, int limit) {
        String userId = securityUtils.getCurrentUserId();
        log.info("Searching similar notes for query: '{}', userId: {}", query, userId);

        try {
            // Query Rewriting: 改写查询以提升检索准确度
            List<String> rewrittenQueries = limitRewriteVariants(queryRewritingService.rewriteQuery(query), query);
            log.info("Query rewritten into {} variants: {}", rewrittenQueries.size(), rewrittenQueries);

            // 对每个改写后的查询进行检索
            ContentRetriever userRetriever = buildContentRetriever(userId);
            List<Content> allContents = retrieveContents(userRetriever, rewrittenQueries);

            // 去重（基于 textSegment 的文本内容）
            List<Content> uniqueContents = allContents.stream()
                    .distinct()
                    .collect(Collectors.toList());

            log.info("Retrieved {} unique content items from {} queries", 
                    uniqueContents.size(), rewrittenQueries.size());

            // 如果有 Reranker 且熔断器未开启，使用原始查询进行重排序
            if (resilientLlmService.isRerankAvailable() && !uniqueContents.isEmpty()) {
                log.info("Applying reranking with ScoringModel (via CircuitBreaker)");
                int candidateCount = Math.min(uniqueContents.size(), Math.max(1, rerankMaxCandidates));
                List<Content> rerankCandidates = uniqueContents.stream()
                        .limit(candidateCount)
                        .collect(Collectors.toList());
                List<TextSegment> segments = rerankCandidates.stream()
                        .map(Content::textSegment)
                        .collect(Collectors.toList());

                // 通过熔断器调用 Rerank，失败时返回 null（跳过 rerank）
                Response<List<Double>> scores = resilientLlmService.scoreAll(segments, query);
                if (scores == null) {
                    log.warn("Rerank call failed or circuit open, skipping rerank");
                } else {
                    List<Double> scoreList = scores.content();

                    // 创建带分数的列表并排序
                    if (scoreList == null || scoreList.size() != rerankCandidates.size()) {
                        log.warn("Rerank returned {} scores for {} candidates, skipping rerank",
                                scoreList == null ? 0 : scoreList.size(), rerankCandidates.size());
                    } else {
                        List<ContentWithScore> scoredContents = new ArrayList<>();
                        for (int i = 0; i < rerankCandidates.size(); i++) {
                            scoredContents.add(new ContentWithScore(rerankCandidates.get(i), scoreList.get(i)));
                        }
                        scoredContents.sort(Comparator.comparingDouble(ContentWithScore::score).reversed());

                        // 更新 contents 为重排序后的结果
                        List<Content> reranked = scoredContents.stream()
                                .map(ContentWithScore::content)
                                .collect(Collectors.toList());
                        reranked.addAll(uniqueContents.stream()
                                .skip(rerankCandidates.size())
                                .collect(Collectors.toList()));
                        uniqueContents = reranked;

                        log.info("Reranking completed, top score: {}",
                                scoredContents.isEmpty() ? "N/A" : scoredContents.get(0).score());
                    }
                }
            }

            // 提取笔记 ID 并去重
            List<String> noteIds = uniqueContents.stream()
                    .map(content -> content.textSegment().metadata().getString("noteId"))
                    .filter(id -> id != null)
                    .distinct()
                    .limit(limit)
                    .collect(Collectors.toList());

            return findOwnedNotesInResultOrder(noteIds, userId).stream()
                    .map(this::toModel)
                    .limit(limit)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error in semantic search: {}", e.getMessage(), e);
            return List.of();
        }
    }

    // 辅助记录类
    private record ContentWithScore(Content content, double score) {}

    /**
     * 直接使用 EmbeddingStore 搜索（带最小相似度阈值）
     */
    @Transactional(readOnly = true)
    public List<Note> searchWithMinScore(String query, int limit, double minScore) {
        String userId = securityUtils.getCurrentUserId();
        return searchWithMinScore(query, limit, minScore, userId);
    }

    /**
     * 直接使用 EmbeddingStore 搜索（带最小相似度阈值）
     * @param userId 用户 ID
     */
    @Transactional(readOnly = true)
    public List<Note> searchWithMinScore(String query, int limit, double minScore, String userId) {
        log.info("Searching with minScore: {}, query: '{}', userId: {}", minScore, query, userId);

        try {
            // 生成查询嵌入（通过熔断器保护）
            TextSegment querySegment = TextSegment.from(query);
            Response<Embedding> response = resilientLlmService.embed(querySegment);
            if (response == null) {
                log.warn("Embedding model unavailable, returning empty results");
                return List.of();
            }
            Embedding queryEmbedding = response.content();

            // 搜索相似文档
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(limit * 3)  // 多取一些，后面过滤
                    .minScore(minScore)
                    .filter(RagFilterFactory.userFilter(userId))
                    .build();

            EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
            List<EmbeddingMatch<TextSegment>> matches = result.matches();

            log.info("Found {} matches with minScore >= {}", matches.size(), minScore);

            // 提取笔记 ID 并去重
            List<String> noteIds = matches.stream()
                    .map(match -> match.embedded().metadata().getString("noteId"))
                    .filter(id -> id != null)
                    .distinct()
                    .limit(limit)
                    .collect(Collectors.toList());

            return findOwnedNotesInResultOrder(noteIds, userId).stream()
                    .map(this::toModel)
                    .limit(limit)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error in search with minScore: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 获取与查询最相关的文本片段（用于 RAG 上下文注入）
     * 使用 Query Rewriting 提升检索准确度
     */
    public List<String> getRelevantContext(String query, int maxSegments, String userId) {
        try {
            ContentRetriever userRetriever = buildContentRetriever(userId);
            List<Content> allContents = userRetriever.retrieve(Query.from(query));

            // 去重并返回文本
            return allContents.stream()
                    .map(content -> content.textSegment().text())
                    .distinct()
                    .limit(maxSegments)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error getting relevant context: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 获取 EmbeddingModel（供外部直接计算 embedding 使用）
     */
    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    private List<com.ainote.app.entity.Note> findOwnedNotesInResultOrder(List<String> noteIds, String userId) {
        if (noteIds == null || noteIds.isEmpty()) {
            return List.of();
        }
        Map<String, com.ainote.app.entity.Note> byId = noteRepository
                .findByIdsAndUserIdAndDeletedAtIsNull(noteIds, userId)
                .stream()
                .collect(Collectors.toMap(com.ainote.app.entity.Note::getId, note -> note));
        return noteIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Note toModel(com.ainote.app.entity.Note entity) {
        Note model = new Note();
        model.setId(entity.getId());
        model.setTitle(entity.getTitle());
        model.setContent(entity.getContent());
        model.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        model.setUpdatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        model.setFolderId(entity.getFolder() != null ? entity.getFolder().getId() : null);
        if (entity.getTags() != null) {
            model.setTags(entity.getTags().stream()
                    .map(tag -> {
                        com.ainote.app.model.Tag t = new com.ainote.app.model.Tag();
                        t.setId(tag.getId());
                        t.setName(tag.getName());
                        return t;
                    })
                    .collect(Collectors.toList()));
        }
        return model;
    }
}
