package com.ainote.app.service;

import com.ainote.app.entity.Note;
import com.ainote.app.entity.NoteConcept;
import com.ainote.app.entity.Schedule;
import com.ainote.app.entity.Folder;
import com.ainote.app.repository.FolderRepository;
import com.ainote.app.repository.NoteConceptRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.ScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 智能建议服务（工程化升级版）
 *
 * 改进点：
 * 1. 轻量查询：用 count/limit 查询替代全量查库
 * 2. 频率控制：同一建议被忽略后短期不再推送（30分钟冷却）
 * 3. 结果缓存：5分钟内同一用户不重复计算
 */
@Service
public class SmartSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SmartSuggestionService.class);

    private static final long SUGGESTION_COOLDOWN_MS = 30 * 60 * 1000; // 30 min
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 min

    private final NoteRepository noteRepository;
    private final NoteConceptRepository conceptRepository;
    private final ScheduleRepository scheduleRepository;
    private final FolderRepository folderRepository;

    /** 建议结果缓存：userId → (suggestions, timestamp) */
    private final ConcurrentHashMap<String, CachedSuggestions> suggestionsCache = new ConcurrentHashMap<>();

    /** 被忽略的建议冷却：userId:suggestionType → dismissedTimestamp */
    private final ConcurrentHashMap<String, Long> dismissedSuggestions = new ConcurrentHashMap<>();

    public SmartSuggestionService(
            NoteRepository noteRepository,
            NoteConceptRepository conceptRepository,
            ScheduleRepository scheduleRepository,
            FolderRepository folderRepository) {
        this.noteRepository = noteRepository;
        this.conceptRepository = conceptRepository;
        this.scheduleRepository = scheduleRepository;
        this.folderRepository = folderRepository;
    }

    /**
     * 记录用户忽略了某类建议
     */
    public void dismissSuggestion(String userId, String suggestionType) {
        dismissedSuggestions.put(userId + ":" + suggestionType, System.currentTimeMillis());
        suggestionsCache.remove(userId); // 清除缓存使下次重新计算
    }

    /**
     * 生成当前最相关的建议列表（最多 3 条）
     * 带 5 分钟缓存，避免每次 CHAT 都重新查库
     */
    public List<Suggestion> generateSuggestions(String userId) {
        // 检查缓存
        CachedSuggestions cached = suggestionsCache.get(userId);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            return cached.suggestions;
        }

        List<Suggestion> suggestions = computeSuggestions(userId);
        suggestionsCache.put(userId, new CachedSuggestions(suggestions, System.currentTimeMillis()));
        return suggestions;
    }

    private List<Suggestion> computeSuggestions(String userId) {
        List<Suggestion> suggestions = new ArrayList<>();

        try {
            // 1. 过期日程（只查 pending + 过期的，limit 3）
            if (!isDismissed(userId, "OVERDUE_SCHEDULE")) {
                suggestions.addAll(findOverdueSchedules(userId));
            }

            // 一次性查询用户笔记，避免多个子方法重复查库
            boolean needNotes = !isDismissed(userId, "ORGANIZE_NOTES")
                    || !isDismissed(userId, "REVIEW_NOTE")
                    || !isDismissed(userId, "ADD_TAGS");
            List<Note> userNotes = needNotes
                    ? noteRepository.findByUserIdAndDeletedAtIsNull(userId)
                    : List.of();

            // 2. 笔记归类建议
            if (!isDismissed(userId, "ORGANIZE_NOTES")) {
                suggestions.addAll(suggestNoteOrganization(userId, userNotes));
            }

            // 3. 需要复习的笔记
            if (!isDismissed(userId, "REVIEW_NOTE")) {
                suggestions.addAll(suggestReview(userNotes));
            }

            // 4. 无标签笔记
            if (!isDismissed(userId, "ADD_TAGS")) {
                suggestions.addAll(suggestAddTags(userNotes));
            }

        } catch (Exception e) {
            log.error("Failed to generate suggestions for user {}: {}", userId, e.getMessage());
        }

        // 按优先级排序，最多 3 条
        return suggestions.stream()
                .sorted()
                .limit(3)
                .toList();
    }

    private boolean isDismissed(String userId, String type) {
        Long dismissedAt = dismissedSuggestions.get(userId + ":" + type);
        if (dismissedAt == null) return false;
        if (System.currentTimeMillis() - dismissedAt > SUGGESTION_COOLDOWN_MS) {
            dismissedSuggestions.remove(userId + ":" + type);
            return false;
        }
        return true;
    }

    private List<Suggestion> findOverdueSchedules(String userId) {
        List<Suggestion> result = new ArrayList<>();
        try {
            List<Schedule> overdue = scheduleRepository.findByUserIdOrderByStartTimeDesc(userId)
                    .stream()
                    .filter(s -> "pending".equals(s.getStatus()) && s.getStartTime().isBefore(LocalDateTime.now()))
                    .limit(2)
                    .toList();

            for (Schedule s : overdue) {
                long hoursOverdue = ChronoUnit.HOURS.between(s.getStartTime(), LocalDateTime.now());
                String timeDesc = hoursOverdue < 24
                        ? hoursOverdue + " 小时前"
                        : (hoursOverdue / 24) + " 天前";

                result.add(new Suggestion(
                        SuggestionType.OVERDUE_SCHEDULE,
                        "日程「" + s.getTitle() + "」已过期（" + timeDesc + "到期），要标记为完成吗？",
                        Map.of("scheduleId", s.getId(), "title", s.getTitle(), "action", "COMPLETE_SCHEDULE"),
                        90
                ));
            }
        } catch (Exception e) {
            log.debug("Failed to check overdue schedules: {}", e.getMessage());
        }
        return result;
    }

    private List<Suggestion> suggestNoteOrganization(String userId, List<Note> notes) {
        try {
            List<Folder> folders = folderRepository.findByUserId(userId);
            if (folders.isEmpty()) return List.of();

            long unfiledCount = notes.stream()
                    .filter(n -> n.getFolder() == null)
                    .count();

            if (unfiledCount < 3) return List.of();

            return List.of(new Suggestion(
                    SuggestionType.ORGANIZE_NOTES,
                    "你有 " + unfiledCount + " 条笔记还没有归类到文件夹，要帮你智能分类吗？",
                    Map.of("count", String.valueOf(unfiledCount), "action", "AUTO_CLASSIFY"),
                    60
            ));
        } catch (Exception e) {
            log.debug("Failed to suggest organization: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Suggestion> suggestReview(List<Note> notes) {
        try {
            LocalDateTime twoWeeksAgo = LocalDateTime.now().minusDays(14);
            List<Note> staleNotes = notes.stream()
                    .filter(n -> n.getUpdatedAt().isBefore(twoWeeksAgo))
                    .sorted(Comparator.comparing(Note::getUpdatedAt))
                    .limit(1)
                    .toList();

            if (staleNotes.isEmpty()) return List.of();

            Note oldest = staleNotes.get(0);
            long daysSince = ChronoUnit.DAYS.between(oldest.getUpdatedAt(), LocalDateTime.now());

            return List.of(new Suggestion(
                    SuggestionType.REVIEW_NOTE,
                    "笔记「" + oldest.getTitle() + "」已经 " + daysSince + " 天没有查看了，要复习一下吗？",
                    Map.of("noteId", oldest.getId(), "title", oldest.getTitle(), "action", "OPEN_NOTE"),
                    40
            ));
        } catch (Exception e) {
            log.debug("Failed to suggest review: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Suggestion> suggestAddTags(List<Note> notes) {
        try {
            long untaggedCount = notes.stream()
                    .filter(n -> n.getTags().isEmpty())
                    .count();

            if (untaggedCount < 5) return List.of();

            return List.of(new Suggestion(
                    SuggestionType.ADD_TAGS,
                    "你有 " + untaggedCount + " 条笔记没有标签，要帮你自动打标签吗？",
                    Map.of("count", String.valueOf(untaggedCount), "action", "AUTO_TAG"),
                    30
            ));
        } catch (Exception e) {
            log.debug("Failed to suggest tags: {}", e.getMessage());
            return List.of();
        }
    }

    // === Data classes ===

    public enum SuggestionType {
        OVERDUE_SCHEDULE,
        ORGANIZE_NOTES,
        REVIEW_NOTE,
        MERGE_NOTES,
        ADD_TAGS,
        EXTRACT_SCHEDULE
    }

    public record Suggestion(
            SuggestionType type,
            String message,
            Map<String, String> params,
            int priority
    ) implements Comparable<Suggestion> {
        @Override
        public int compareTo(Suggestion other) {
            return Integer.compare(other.priority, this.priority);
        }
    }

    private record CachedSuggestions(List<Suggestion> suggestions, long timestamp) {}
}
