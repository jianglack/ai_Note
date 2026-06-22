package com.ainote.app.service;

import com.ainote.app.entity.RagFeedback;
import com.ainote.app.repository.RagFeedbackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RAG 反馈服务（工程化升级版）
 *
 * 改进点：
 * 1. 按用户独立计算阈值（不同用户笔记风格不同）
 * 2. 冷启动渐进校准（新用户从 0.5 开始，逐步调到合适值）
 * 3. 保留全局兜底 + 向后兼容无参 getAdaptiveThreshold()
 */
@Service
public class RagFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(RagFeedbackService.class);

    private final RagFeedbackRepository repository;

    @Value("${app.context.rag-min-score:0.7}")
    private double defaultThreshold;

    @Value("${app.rag.feedback.window-days:30}")
    private int feedbackWindowDays;

    /** 冷启动阈值（新用户反馈不足时使用，比默认值低以获取更多结果） */
    private static final double COLD_START_THRESHOLD = 0.5;

    /** 最少反馈数量，低于此值视为冷启动 */
    private static final int MIN_FEEDBACK_COUNT = 10;

    /** 按用户缓存：userId → (threshold, timestamp) */
    private final ConcurrentHashMap<String, CachedThreshold> userCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 min

    public RagFeedbackService(RagFeedbackRepository repository) {
        this.repository = repository;
    }

    public void recordFeedback(String userId, String query, String resultNoteId,
                                double similarityScore, String feedbackType) {
        double weight = switch (feedbackType) {
            case "THUMBS_UP", "THUMBS_DOWN" -> 1.0;
            case "CLICK" -> 0.3;
            default -> 0.5;
        };

        RagFeedback feedback = new RagFeedback();
        feedback.setUserId(userId);
        feedback.setQuery(query);
        feedback.setResultNoteId(resultNoteId);
        feedback.setSimilarityScore(similarityScore);
        feedback.setFeedbackType(feedbackType);
        feedback.setWeight(weight);
        repository.save(feedback);

        // 失效该用户的缓存
        userCache.remove(userId);
        log.info("RAG feedback recorded: type={}, score={}, user={}", feedbackType, similarityScore, userId);
    }

    /**
     * 按用户独立计算自适应阈值
     */
    public double getAdaptiveThreshold(String userId) {
        // 检查缓存
        CachedThreshold cached = userCache.get(userId);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            return cached.threshold;
        }

        double threshold = calculateUserThreshold(userId);
        userCache.put(userId, new CachedThreshold(threshold, System.currentTimeMillis()));
        return threshold;
    }

    /**
     * 向后兼容：无参版本使用全局默认
     */
    public double getAdaptiveThreshold() {
        return defaultThreshold;
    }

    private double calculateUserThreshold(String userId) {
        try {
            LocalDateTime since = LocalDateTime.now().minusDays(feedbackWindowDays);
            List<RagFeedback> feedbacks = repository.findRecentByUser(userId, since);

            if (feedbacks.size() < MIN_FEEDBACK_COUNT) {
                // 冷启动：反馈不足，使用渐进式阈值
                // 随反馈数量增加，从 COLD_START_THRESHOLD 向 defaultThreshold 靠拢
                double progress = (double) feedbacks.size() / MIN_FEEDBACK_COUNT;
                double coldThreshold = COLD_START_THRESHOLD + (defaultThreshold - COLD_START_THRESHOLD) * progress;
                log.debug("Cold start threshold for user {}: {} (feedbacks: {})", userId, coldThreshold, feedbacks.size());
                return Math.round(coldThreshold * 100.0) / 100.0;
            }

            double positiveWeightedSum = 0;
            double positiveWeightTotal = 0;
            double negativeWeightedSum = 0;
            double negativeWeightTotal = 0;

            for (RagFeedback f : feedbacks) {
                if ("THUMBS_UP".equals(f.getFeedbackType()) || "CLICK".equals(f.getFeedbackType())) {
                    positiveWeightedSum += f.getSimilarityScore() * f.getWeight();
                    positiveWeightTotal += f.getWeight();
                } else if ("THUMBS_DOWN".equals(f.getFeedbackType())) {
                    negativeWeightedSum += f.getSimilarityScore() * f.getWeight();
                    negativeWeightTotal += f.getWeight();
                }
            }

            double threshold = defaultThreshold;

            if (positiveWeightTotal > 0 && negativeWeightTotal > 0) {
                double avgPositiveScore = positiveWeightedSum / positiveWeightTotal;
                double avgNegativeScore = negativeWeightedSum / negativeWeightTotal;
                threshold = (avgPositiveScore + avgNegativeScore) / 2.0;
            } else if (positiveWeightTotal > 0) {
                double avgPositiveScore = positiveWeightedSum / positiveWeightTotal;
                threshold = avgPositiveScore * 0.85;
            }

            // Clamp to [0.4, 0.95]
            threshold = Math.max(0.4, Math.min(0.95, threshold));
            log.info("Adaptive threshold for user {}: {} (from {} feedbacks)", userId, threshold, feedbacks.size());
            return threshold;

        } catch (Exception e) {
            log.warn("Failed to calculate threshold for user {}, using default: {}", userId, e.getMessage());
            return defaultThreshold;
        }
    }

    private record CachedThreshold(double threshold, long timestamp) {}
}
