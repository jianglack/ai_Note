package com.ainote.app.service;

import com.ainote.app.entity.Note;
import com.ainote.app.entity.NoteConcept;
import com.ainote.app.entity.Schedule;
import com.ainote.app.repository.NoteConceptRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.ScheduleRepository;
import com.ainote.app.util.PromptLoader;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 笔记洞察服务
 * 提供跨笔记推理：主题聚类、模式检测、统计概览、智能建议
 */
@Service
public class NoteInsightService {

    private static final Logger log = LoggerFactory.getLogger(NoteInsightService.class);

    private final NoteRepository noteRepository;
    private final NoteConceptRepository conceptRepository;
    private final ScheduleRepository scheduleRepository;
    private final ChatModel chatModel;
    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NoteInsightService(
            NoteRepository noteRepository,
            NoteConceptRepository conceptRepository,
            ScheduleRepository scheduleRepository,
            ChatModel chatModel,
            PromptLoader promptLoader) {
        this.noteRepository = noteRepository;
        this.conceptRepository = conceptRepository;
        this.scheduleRepository = scheduleRepository;
        this.chatModel = chatModel;
        this.promptLoader = promptLoader;
    }

    /**
     * 用户笔记统计概览
     */
    public Map<String, Object> getStatistics(String userId) {
        List<Note> notes = noteRepository.findByUserIdAndDeletedAtIsNull(userId);
        List<NoteConcept> concepts = conceptRepository.findByUserId(userId);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalNotes", notes.size());
        stats.put("totalConcepts", concepts.stream().map(NoteConcept::getConcept).distinct().count());

        // 按文件夹分组
        Map<String, Long> byFolder = notes.stream()
                .collect(Collectors.groupingBy(
                        n -> n.getFolder() != null ? n.getFolder().getName() : "未分类",
                        Collectors.counting()));
        stats.put("notesByFolder", byFolder);

        // 最近活跃（7天内更新的笔记数）
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        long recentCount = notes.stream().filter(n -> n.getUpdatedAt().isAfter(weekAgo)).count();
        stats.put("recentlyActive", recentCount);

        // 最近创建
        notes.stream()
                .sorted(Comparator.comparing(Note::getCreatedAt).reversed())
                .limit(5)
                .forEach(n -> {}); // just for counting
        List<String> recentTitles = notes.stream()
                .sorted(Comparator.comparing(Note::getCreatedAt).reversed())
                .limit(5)
                .map(Note::getTitle)
                .toList();
        stats.put("recentNotes", recentTitles);

        // Top 概念
        Map<String, Long> conceptFreq = concepts.stream()
                .collect(Collectors.groupingBy(NoteConcept::getConcept, Collectors.counting()));
        List<String> topConcepts = conceptFreq.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(Map.Entry::getKey)
                .toList();
        stats.put("topConcepts", topConcepts);

        return stats;
    }

    /**
     * LLM 驱动的深度洞察分析
     */
    public String analyzeInsights(String userId) {
        List<Note> notes = noteRepository.findByUserIdAndDeletedAtIsNull(userId);
        if (notes.size() < 3) {
            return "{\"message\":\"笔记数量不足（至少需要 3 条），暂时无法提供洞察分析\"}";
        }

        // 构建笔记摘要列表（不发送完整内容以节省 token）
        StringBuilder noteList = new StringBuilder();
        for (int i = 0; i < Math.min(notes.size(), 50); i++) {
            Note n = notes.get(i);
            noteList.append(String.format("%d. 【%s】", i + 1, n.getTitle()));
            if (n.getFolder() != null) {
                noteList.append(" [文件夹: ").append(n.getFolder().getName()).append("]");
            }
            if (!n.getTags().isEmpty()) {
                noteList.append(" [标签: ").append(
                        n.getTags().stream().map(t -> t.getName()).collect(Collectors.joining(", ")));
                noteList.append("]");
            }
            // 内容摘要（前100字）
            String preview = n.getContent() != null && n.getContent().length() > 100
                    ? n.getContent().substring(0, 100) + "..."
                    : (n.getContent() != null ? n.getContent() : "");
            preview = preview.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
            if (!preview.isEmpty()) {
                noteList.append(" 摘要: ").append(preview);
            }
            noteList.append("\n");
        }

        try {
            List<ChatMessage> messages = List.of(
                    SystemMessage.from(promptLoader.load("note-insight.txt")),
                    UserMessage.from(noteList.toString())
            );

            ChatResponse response = chatModel.chat(ChatRequest.builder().messages(messages).build());
            String result = response.aiMessage().text();

            // Clean markdown wrapping
            if (result != null) {
                result = result.trim();
                if (result.startsWith("```json")) result = result.substring(7);
                else if (result.startsWith("```")) result = result.substring(3);
                if (result.endsWith("```")) result = result.substring(0, result.length() - 3);
                result = result.trim();
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to analyze insights: {}", e.getMessage());
            return "{\"error\":\"洞察分析失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 查找可能需要合并的重复/相似笔记
     */
    public List<Map<String, Object>> findDuplicateCandidates(String userId) {
        List<NoteConcept> concepts = conceptRepository.findByUserId(userId);

        // 按 noteId 分组概念
        Map<String, Set<String>> noteConceptSets = concepts.stream()
                .collect(Collectors.groupingBy(
                        NoteConcept::getNoteId,
                        Collectors.mapping(NoteConcept::getConcept, Collectors.toSet())));

        List<Map<String, Object>> candidates = new ArrayList<>();
        List<String> noteIds = new ArrayList<>(noteConceptSets.keySet());

        for (int i = 0; i < noteIds.size() && candidates.size() < 5; i++) {
            for (int j = i + 1; j < noteIds.size() && candidates.size() < 5; j++) {
                Set<String> set1 = noteConceptSets.get(noteIds.get(i));
                Set<String> set2 = noteConceptSets.get(noteIds.get(j));

                // Jaccard similarity
                Set<String> intersection = new HashSet<>(set1);
                intersection.retainAll(set2);
                Set<String> union = new HashSet<>(set1);
                union.addAll(set2);

                if (!union.isEmpty()) {
                    double similarity = (double) intersection.size() / union.size();
                    if (similarity >= 0.5) {
                        // Fetch note titles
                        Optional<Note> n1 = noteRepository.findById(noteIds.get(i));
                        Optional<Note> n2 = noteRepository.findById(noteIds.get(j));
                        if (n1.isPresent() && n2.isPresent()) {
                            Map<String, Object> candidate = new LinkedHashMap<>();
                            candidate.put("note1", Map.of("id", n1.get().getId(), "title", n1.get().getTitle()));
                            candidate.put("note2", Map.of("id", n2.get().getId(), "title", n2.get().getTitle()));
                            candidate.put("similarity", Math.round(similarity * 100) + "%");
                            candidate.put("sharedConcepts", intersection);
                            candidates.add(candidate);
                        }
                    }
                }
            }
        }

        return candidates;
    }

    /**
     * 获取用户的活动时间线（最近操作）
     */
    public List<Map<String, Object>> getActivityTimeline(String userId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm");

        List<Map<String, Object>> timeline = new ArrayList<>();

        // 最近更新的笔记
        List<Note> recentNotes = noteRepository.findByUserIdAndDeletedAtIsNull(userId).stream()
                .filter(n -> n.getUpdatedAt().isAfter(since))
                .sorted(Comparator.comparing(Note::getUpdatedAt).reversed())
                .limit(20)
                .toList();

        for (Note n : recentNotes) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("time", n.getUpdatedAt().format(fmt));
            event.put("type", n.getCreatedAt().equals(n.getUpdatedAt()) ? "created" : "updated");
            event.put("title", n.getTitle());
            timeline.add(event);
        }

        // 最近的日程
        List<Schedule> recentSchedules = scheduleRepository.findByUserIdOrderByStartTimeDesc(userId).stream()
                .filter(s -> s.getCreatedAt().isAfter(since))
                .limit(10)
                .toList();

        for (Schedule s : recentSchedules) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("time", s.getCreatedAt().format(fmt));
            event.put("type", "schedule");
            event.put("title", s.getTitle());
            event.put("status", s.getStatus());
            timeline.add(event);
        }

        // Sort by time desc
        timeline.sort((a, b) -> ((String) b.get("time")).compareTo((String) a.get("time")));

        return timeline.stream().limit(20).toList();
    }
}
