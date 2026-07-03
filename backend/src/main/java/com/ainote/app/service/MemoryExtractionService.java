package com.ainote.app.service;

import com.ainote.app.config.MemoryProperties;
import com.ainote.app.entity.EpisodicMemory;
import com.ainote.app.entity.SemanticMemory;
import com.ainote.app.entity.UserMemory;
import com.ainote.app.repository.EpisodicMemoryRepository;
import com.ainote.app.repository.SemanticMemoryRepository;
import com.ainote.app.repository.UserMemoryRepository;
import com.ainote.app.util.PromptLoader;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 记忆提炼服务（工程化升级版）
 *
 * 改进点：
 * 1. 语义去重：使用 embedding cosine similarity（>0.85）替代精确字符串匹配
 * 2. 冲突检测：相似度高但内容变化时，用新内容覆盖旧记忆
 * 3. 容量管理：每用户最多 MAX_MEMORIES_PER_USER 条，超出自动淘汰最低分
 * 4. 衰减分数：score = confidence × log2(timesReinforced+1) × decay(daysSinceLastReinforced)
 * 5. 情节-语义联动：情节摘要生成时同步提取语义记忆
 */
@Service
public class MemoryExtractionService {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractionService.class);

    /** 语义去重阈值：cosine similarity > 此值视为同一条记忆 */
    private static final double SIMILARITY_THRESHOLD = 0.85;

    /** 每用户最大语义记忆条数 */
    private static final int MAX_MEMORIES_PER_USER = 50;

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final SemanticMemoryRepository semanticMemoryRepository;
    private final EpisodicMemoryRepository episodicMemoryRepository;
    private final UserMemoryRepository userMemoryRepository;
    private final ObjectMapper objectMapper;
    private final PromptLoader promptLoader;
    private final MemoryProperties memoryProperties;
    private final MemoryCapturePolicy memoryCapturePolicy;

    public MemoryExtractionService(
            ChatModel chatModel,
            EmbeddingModel embeddingModel,
            SemanticMemoryRepository semanticMemoryRepository,
            EpisodicMemoryRepository episodicMemoryRepository,
            UserMemoryRepository userMemoryRepository,
            PromptLoader promptLoader,
            ObjectMapper objectMapper,
            MemoryProperties memoryProperties,
            MemoryCapturePolicy memoryCapturePolicy) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.semanticMemoryRepository = semanticMemoryRepository;
        this.episodicMemoryRepository = episodicMemoryRepository;
        this.userMemoryRepository = userMemoryRepository;
        this.objectMapper = objectMapper;
        this.promptLoader = promptLoader;
        this.memoryProperties = memoryProperties;
        this.memoryCapturePolicy = memoryCapturePolicy;
        log.info("MemoryExtractionService initialized (fuzzy dedup + decay + capacity management)");
    }

    /**
     * 异步提取语义记忆（每轮对话后调用）
     */
    @Async("securityExecutor")
    public void extractSemanticMemoryAsync(String userId, String userMessage, String aiResponse) {
        long startedAt = System.nanoTime();
        if (!memoryProperties.getCapture().isEnabled()) {
            logExtractionResult(userId, "skipped", 0, 0, 0, 0, startedAt, "capture_disabled");
            return;
        }
        MemoryCapturePolicy.CaptureDecision decision = memoryCapturePolicy.evaluate(
                new MemoryCapturePolicy.CaptureRequest(userId, userMessage, aiResponse));
        if (!decision.allowed()) {
            logExtractionResult(userId, "skipped", 0, 0, 0, 0, startedAt, decision.reason());
            return;
        }

        int candidateCount = 0;
        int saved = 0;
        int reinforced = 0;
        int conflictUpdated = 0;
        try {
            log.debug("Starting semantic memory extraction for user: {}", userId);

            String conversation = "用户：" + userMessage + "\nAI：" + aiResponse;

            List<ChatMessage> messages = List.of(
                    SystemMessage.from(promptLoader.load("semantic-extraction.txt")),
                    UserMessage.from(conversation)
            );

            ChatResponse response = chatModel.chat(ChatRequest.builder().messages(messages).build());
            String result = response.aiMessage().text();

            if (result == null || result.isBlank()) {
                log.debug("No semantic memory extracted for user: {}", userId);
                logExtractionResult(userId, "succeeded", 0, 0, 0, 0, startedAt, "blank_response");
                return;
            }

            result = cleanJsonResponse(result);

            List<Map<String, Object>> extracted = objectMapper.readValue(
                    result, new TypeReference<List<Map<String, Object>>>() {});

            if (extracted.isEmpty()) {
                log.debug("Empty extraction result for user: {}", userId);
                logExtractionResult(userId, "succeeded", 0, 0, 0, 0, startedAt, "empty_candidates");
                return;
            }

            candidateCount = extracted.size();

            for (Map<String, Object> item : extracted) {
                String category = (String) item.get("category");
                String content = (String) item.get("content");
                double confidence = item.get("confidence") instanceof Number
                        ? ((Number) item.get("confidence")).doubleValue() : 0.8;

                if (category == null || content == null || content.isBlank()) continue;

                int[] result2 = saveOrUpdateMemory(userId, category, content, confidence);
                saved += result2[0];
                reinforced += result2[1];
                conflictUpdated += result2[2];
            }

            // 容量管理：淘汰超出上限的低分记忆
            evictIfOverCapacity(userId);

            log.info("Semantic extraction for user: {}, new: {}, reinforced: {}, conflict-updated: {}, total extracted: {}",
                    userId, saved, reinforced, conflictUpdated, extracted.size());
            logExtractionResult(userId, "succeeded", candidateCount, saved, reinforced, conflictUpdated, startedAt, "completed");

        } catch (Exception e) {
            log.warn("Semantic memory extraction failed for user: {}: {}", userId, e.getMessage());
            logExtractionResult(userId, "failed", candidateCount, saved, reinforced, conflictUpdated, startedAt,
                    e.getClass().getSimpleName());
        }
    }

    /**
     * 核心：保存或更新一条语义记忆
     * 返回 [newCount, reinforcedCount, conflictUpdatedCount]
     *
     * 策略：
     * 1. 计算新内容的 embedding
     * 2. 用向量相似度查找已有记忆（cosine > threshold）
     * 3. 如果找到高度相似的：
     *    a. 内容几乎相同 → reinforce（增加印证次数）
     *    b. 内容有变化 → conflict update（更新内容，保留历史）
     * 4. 没找到 → 创建新记忆
     */
    private int[] saveOrUpdateMemory(String userId, String category, String content, double confidence) {
        try {
            // Step 1: 计算 embedding
            float[] contentEmbedding = computeEmbedding(content);

            if (contentEmbedding != null) {
                // Step 2: 向量相似度搜索
                String embeddingStr = embeddingToString(contentEmbedding);
                List<SemanticMemory> similar = semanticMemoryRepository
                        .findSimilarByEmbedding(userId, embeddingStr, similarityThreshold(), 3);

                if (!similar.isEmpty()) {
                    SemanticMemory existing = similar.get(0);

                    // 判断是 reinforce 还是 conflict update
                    if (isContentSimilar(existing.getContent(), content)) {
                        // 内容几乎相同 → reinforce
                        existing.setTimesReinforced(existing.getTimesReinforced() + 1);
                        existing.setLastReinforcedAt(LocalDateTime.now());
                        if (confidence > existing.getConfidence()) {
                            existing.setConfidence(confidence);
                        }
                        updateDecayScore(existing);
                        semanticMemoryRepository.save(existing);
                        log.debug("Reinforced semantic memory (embedding match): {}", content);
                        logMemoryWrite(userId, "reinforced", category);
                        return new int[]{0, 1, 0};
                    } else {
                        // 语义相似但内容有变化 → conflict update（用新内容覆盖）
                        log.info("Conflict detected: old='{}' → new='{}'", existing.getContent(), content);
                        existing.setContent(content);
                        existing.setCategory(category);
                        existing.setConfidence(confidence);
                        existing.setEmbedding(contentEmbedding);
                        existing.setLastReinforcedAt(LocalDateTime.now());
                        existing.setTimesReinforced(existing.getTimesReinforced() + 1);
                        updateDecayScore(existing);
                        semanticMemoryRepository.save(existing);
                        persistEmbedding(existing, contentEmbedding);
                        logMemoryWrite(userId, "conflict_updated", category);
                        return new int[]{0, 0, 1};
                    }
                }
            }

            // Step 3: 精确匹配后备（embedding 计算失败时）
            if (contentEmbedding == null) {
                List<SemanticMemory> exactMatch = semanticMemoryRepository
                        .findByUserIdAndContent(userId, content);
                if (!exactMatch.isEmpty()) {
                    SemanticMemory mem = exactMatch.get(0);
                    mem.setTimesReinforced(mem.getTimesReinforced() + 1);
                    mem.setLastReinforcedAt(LocalDateTime.now());
                    updateDecayScore(mem);
                    semanticMemoryRepository.save(mem);
                    logMemoryWrite(userId, "reinforced", mem.getCategory());
                    return new int[]{0, 1, 0};
                }
            }

            // Step 4: 新建记忆
            SemanticMemory mem = new SemanticMemory();
            mem.setUserId(userId);
            mem.setCategory(category);
            mem.setContent(content);
            mem.setConfidence(confidence);
            mem.setSource("ai_extracted");
            mem.setEmbedding(contentEmbedding);
            mem.setLastReinforcedAt(LocalDateTime.now());
            updateDecayScore(mem);
            semanticMemoryRepository.save(mem);
            persistEmbedding(mem, contentEmbedding);
            log.debug("Saved new semantic memory: [{}] {}", category, content);
            logMemoryWrite(userId, "created", category);
            return new int[]{1, 0, 0};

        } catch (Exception e) {
            log.warn("Failed to save/update semantic memory: {}", e.getMessage());
            return new int[]{0, 0, 0};
        }
    }

    /**
     * 计算文本的 embedding 向量
     */
    private float[] computeEmbedding(String text) {
        try {
            Embedding embedding = embeddingModel.embed(text).content();
            return embedding.vector();
        } catch (Exception e) {
            log.warn("Failed to compute embedding for text: {}", e.getMessage());
            return null;
        }
    }

    /**
     * float[] → pgvector 格式字符串 "[0.1,0.2,...]"
     */
    private String embeddingToString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private void persistEmbedding(SemanticMemory memory, float[] embedding) {
        if (memory.getId() == null || embedding == null) {
            return;
        }
        semanticMemoryRepository.updateEmbedding(memory.getId(), embeddingToString(embedding));
    }

    /**
     * 判断两段文本内容是否"几乎相同"（字符级相似度 > 0.7）
     * 用于区分 reinforce（同一事实被再次提及）和 conflict（事实发生变化）
     */
    private boolean isContentSimilar(String existing, String newContent) {
        if (existing == null || newContent == null) return false;
        String a = existing.trim().toLowerCase();
        String b = newContent.trim().toLowerCase();

        // 简单的 Jaccard 相似度（基于字符 bigram）
        if (a.equals(b)) return true;
        if (a.length() < 2 || b.length() < 2) return a.equals(b);

        var bigramsA = toBigrams(a);
        var bigramsB = toBigrams(b);

        long intersection = bigramsA.stream().filter(bigramsB::contains).count();
        long union = bigramsA.size() + bigramsB.size() - intersection;

        return union > 0 && (double) intersection / union > 0.7;
    }

    private List<String> toBigrams(String text) {
        List<String> bigrams = new ArrayList<>();
        for (int i = 0; i < text.length() - 1; i++) {
            bigrams.add(text.substring(i, i + 2));
        }
        return bigrams;
    }

    /**
     * 计算并更新衰减分数
     * score = confidence × log2(timesReinforced + 1) × decay(daysSinceLastReinforced)
     * decay = 1 / (1 + days / DECAY_HALF_LIFE_DAYS)
     */
    private void updateDecayScore(SemanticMemory mem) {
        double confidence = mem.getConfidence() != null ? mem.getConfidence() : 0.8;
        int reinforced = mem.getTimesReinforced() != null ? mem.getTimesReinforced() : 1;

        LocalDateTime lastReinforced = mem.getLastReinforcedAt();
        double daysSince = 0;
        if (lastReinforced != null) {
            daysSince = ChronoUnit.DAYS.between(lastReinforced, LocalDateTime.now());
            if (daysSince < 0) daysSince = 0;
        }

        double decay = 1.0 / (1.0 + daysSince / decayHalfLifeDays());
        double reinforceFactor = Math.log(reinforced + 1) / Math.log(2); // log2(n+1)
        double score = confidence * reinforceFactor * decay;

        mem.setDecayScore(Math.round(score * 10000.0) / 10000.0); // 保留4位小数
    }

    /**
     * 批量刷新用户所有语义记忆的衰减分数
     * 可以由定时任务调用（如每天凌晨执行一次）
     */
    @Transactional
    public void refreshDecayScores(String userId) {
        List<SemanticMemory> all = semanticMemoryRepository.findByUserId(userId);
        for (SemanticMemory mem : all) {
            updateDecayScore(mem);
        }
        semanticMemoryRepository.saveAll(all);
        log.debug("Refreshed decay scores for user: {}, count: {}", userId, all.size());
    }

    /**
     * 容量管理：如果用户记忆数超过上限，淘汰得分最低的
     */
    private void evictIfOverCapacity(String userId) {
        long count = semanticMemoryRepository.countByUserId(userId);
        int limit = memoryProperties.getMaxPerUser() > 0 ? memoryProperties.getMaxPerUser() : MAX_MEMORIES_PER_USER;

        if (count <= limit) return;

        int toEvict = (int) (count - limit);
        List<SemanticMemory> lowest = semanticMemoryRepository.findLowestScored(userId, toEvict);

        if (!lowest.isEmpty()) {
            List<Long> ids = lowest.stream().map(SemanticMemory::getId).collect(Collectors.toList());
            semanticMemoryRepository.deleteByIds(ids);
            log.info("Evicted {} low-score semantic memories for user: {}, remaining: {}",
                    ids.size(), userId, limit);
        }
    }

    /**
     * 生成情节摘要（会话过期时调用）
     * 升级：生成摘要后同步触发语义记忆提取
     */
    private double similarityThreshold() {
        return memoryProperties.getSimilarityThreshold() > 0
                ? memoryProperties.getSimilarityThreshold()
                : SIMILARITY_THRESHOLD;
    }

    private double decayHalfLifeDays() {
        return memoryProperties.getDecayHalfLifeDays() > 0
                ? memoryProperties.getDecayHalfLifeDays()
                : 30.0;
    }

    private void logExtractionResult(String userId,
                                     String status,
                                     int candidateCount,
                                     int savedCount,
                                     int reinforcedCount,
                                     int conflictUpdatedCount,
                                     long startedAt,
                                     String reason) {
        if (!memoryProperties.getAudit().isEnabled()) {
            return;
        }
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
        log.info("memory_event=semantic_extraction user_id={} status={} reason={} attempted=true " +
                        "orchestrator_enabled={} capture_enabled={} capture_mode={} audit_enabled={} " +
                        "candidate_count={} saved_count={} reinforced_count={} conflict_updated_count={} duration_ms={}",
                userId,
                status,
                reason,
                memoryProperties.getOrchestrator().isEnabled(),
                memoryProperties.getCapture().isEnabled(),
                memoryProperties.getCapture().getMode().name().toLowerCase(Locale.ROOT),
                memoryProperties.getAudit().isEnabled(),
                candidateCount,
                savedCount,
                reinforcedCount,
                conflictUpdatedCount,
                durationMs);
    }

    private void logMemoryWrite(String userId, String action, String category) {
        if (!memoryProperties.getAudit().isEnabled()) {
            return;
        }
        log.info("memory_event=semantic_memory_write user_id={} action={} category={} audit_enabled={}",
                userId, action, category, memoryProperties.getAudit().isEnabled());
    }

    public void generateEpisodicSummary(String userId) {
        try {
            List<UserMemory> memories = userMemoryRepository.findAllByUserIdOrderByCreatedAtAsc(userId);

            if (memories.isEmpty() || memories.size() < 2) {
                log.debug("Not enough messages for episodic summary, user: {}", userId);
                return;
            }

            String conversationText = memories.stream()
                    .filter(m -> "USER".equals(m.getMessageType()) || "AI".equals(m.getMessageType()))
                    .map(m -> ("USER".equals(m.getMessageType()) ? "用户：" : "AI：") + m.getContent())
                    .collect(Collectors.joining("\n"));

            if (conversationText.isBlank()) return;

            if (conversationText.length() > 3000) {
                conversationText = conversationText.substring(0, 3000) + "\n...(对话内容已截断)";
            }

            // 生成情节摘要
            EpisodicMemory episodic = generateEpisodicFromText(userId, conversationText);

            if (episodic != null) {
                int messageCount = (int) memories.stream()
                        .filter(m -> "USER".equals(m.getMessageType()))
                        .count();
                episodic.setMessageCount(messageCount);
                episodicMemoryRepository.save(episodic);

                log.info("Episodic summary saved for user: {}, messages: {}", userId, messageCount);
            }

            // 联动：从即将被丢弃的对话中再次提取语义记忆（避免信息丢失）
            extractSemanticFromConversationText(userId, conversationText);

        } catch (Exception e) {
            log.warn("Episodic summary generation failed for user: {}: {}", userId, e.getMessage());
        }
    }

    /**
     * 从已拼好的对话文本生成情节摘要（TokenWindow 裁剪时调用）
     * 升级：同步触发语义记忆提取
     */
    @Async("securityExecutor")
    public void generateEpisodicSummaryFromText(String userId, String conversationText, int userMessageCount) {
        try {
            log.debug("Generating episodic summary from trimmed text for user: {}", userId);

            if (conversationText.length() > 3000) {
                conversationText = conversationText.substring(0, 3000) + "\n...(对话内容已截断)";
            }

            EpisodicMemory episodic = generateEpisodicFromText(userId, conversationText);

            if (episodic != null) {
                episodic.setMessageCount(userMessageCount);
                episodicMemoryRepository.save(episodic);

                log.info("Episodic summary (from trimmed) saved for user: {}, turns: {}",
                        userId, userMessageCount);
            }

            // 联动：从被裁掉的对话中提取语义记忆
            extractSemanticFromConversationText(userId, conversationText);

        } catch (Exception e) {
            log.warn("Episodic summary from trimmed text failed for user: {}: {}", userId, e.getMessage());
        }
    }

    /**
     * 内部方法：从对话文本生成 EpisodicMemory（不保存，由调用方保存）
     */
    private EpisodicMemory generateEpisodicFromText(String userId, String conversationText) {
        try {
            List<ChatMessage> messages = List.of(
                    SystemMessage.from(promptLoader.load("episodic-summary.txt")),
                    UserMessage.from(conversationText)
            );

            ChatResponse response = chatModel.chat(ChatRequest.builder().messages(messages).build());
            String result = response.aiMessage().text();

            if (result == null || result.isBlank()) return null;

            result = cleanJsonResponse(result);
            Map<String, Object> parsed = objectMapper.readValue(
                    result, new TypeReference<Map<String, Object>>() {});

            String summary = (String) parsed.get("summary");
            if (summary == null || summary.isBlank()) return null;

            EpisodicMemory episodic = new EpisodicMemory();
            episodic.setUserId(userId);
            episodic.setSessionSummary(summary);

            Object keyTopics = parsed.get("key_topics");
            if (keyTopics != null) {
                episodic.setKeyTopics(objectMapper.writeValueAsString(keyTopics));
            }

            Object actionsTaken = parsed.get("actions_taken");
            if (actionsTaken != null) {
                episodic.setActionsTaken(objectMapper.writeValueAsString(actionsTaken));
            }

            return episodic;
        } catch (Exception e) {
            log.warn("Failed to generate episodic text for user: {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 联动：从对话文本中提取语义记忆
     * 将对话文本按轮次拆分，每轮调用语义提取
     */
    private void extractSemanticFromConversationText(String userId, String conversationText) {
        try {
            List<ChatMessage> messages = List.of(
                    SystemMessage.from(promptLoader.load("semantic-extraction.txt")),
                    UserMessage.from(conversationText)
            );

            ChatResponse response = chatModel.chat(ChatRequest.builder().messages(messages).build());
            String result = response.aiMessage().text();

            if (result == null || result.isBlank()) return;

            result = cleanJsonResponse(result);
            List<Map<String, Object>> extracted = objectMapper.readValue(
                    result, new TypeReference<List<Map<String, Object>>>() {});

            int saved = 0;
            for (Map<String, Object> item : extracted) {
                String category = (String) item.get("category");
                String content = (String) item.get("content");
                double confidence = item.get("confidence") instanceof Number
                        ? ((Number) item.get("confidence")).doubleValue() : 0.8;

                if (category == null || content == null || content.isBlank()) continue;

                int[] counts = saveOrUpdateMemory(userId, category, content, confidence);
                saved += counts[0];
            }

            if (saved > 0) {
                evictIfOverCapacity(userId);
                log.info("Episodic-semantic linkage: extracted {} new memories from trimmed conversation for user: {}",
                        saved, userId);
            }
        } catch (Exception e) {
            log.debug("Semantic extraction from conversation text failed: {}", e.getMessage());
        }
    }

    /**
     * 清理 AI 返回的 JSON（去掉 markdown 代码块包裹）
     */
    private String cleanJsonResponse(String response) {
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }
}
