package com.ainote.app.service;

import com.ainote.app.entity.Note;
import com.ainote.app.entity.NoteConcept;
import com.ainote.app.entity.Schedule;
import com.ainote.app.repository.NoteConceptRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.repository.ScheduleRepository;
import com.ainote.app.util.PromptLoader;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NoteInsightService {

    private static final Logger log = LoggerFactory.getLogger(NoteInsightService.class);

    private final NoteRepository noteRepository;
    private final NoteConceptRepository conceptRepository;
    private final ScheduleRepository scheduleRepository;
    private final ChatModel chatModel;
    private final PromptLoader promptLoader;

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

    public Map<String, Object> getStatistics(String userId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalNotes", noteRepository.countByUserIdAndDeletedAtIsNull(userId));
        stats.put("totalConcepts", conceptRepository.countDistinctConceptsByUserId(userId));

        Map<String, Long> byFolder = noteRepository.countNotesByFolder(userId).stream()
                .collect(Collectors.toMap(
                        NoteRepository.FolderCount::getFolderName,
                        projection -> projection.getNoteCount() == null ? 0L : projection.getNoteCount(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        stats.put("notesByFolder", byFolder);

        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        stats.put("recentlyActive", noteRepository.countRecentlyActiveByUserId(userId, weekAgo));
        stats.put("recentNotes", noteRepository.findRecentTitlesByUserId(userId, PageRequest.of(0, 5)));
        stats.put("topConcepts", conceptRepository.findTopConceptsByUserId(userId, PageRequest.of(0, 10)));

        return stats;
    }

    public String analyzeInsights(String userId) {
        List<Note> notes = noteRepository.findByUserIdAndDeletedAtIsNull(userId);
        if (notes.size() < 3) {
            return "{\"message\":\"\u7b14\u8bb0\u6570\u91cf\u4e0d\u8db3\uff08\u81f3\u5c11\u9700\u8981 3 \u6761\uff09\uff0c\u6682\u65f6\u65e0\u6cd5\u63d0\u4f9b\u6d1e\u5bdf\u5206\u6790\"}";
        }

        StringBuilder noteList = new StringBuilder();
        for (int i = 0; i < Math.min(notes.size(), 50); i++) {
            Note note = notes.get(i);
            noteList.append(String.format("%d. %s", i + 1, note.getTitle()));
            if (note.getFolder() != null) {
                noteList.append(" [Folder: ").append(note.getFolder().getName()).append("]");
            }
            if (!note.getTags().isEmpty()) {
                noteList.append(" [Tags: ").append(
                        note.getTags().stream().map(tag -> tag.getName()).collect(Collectors.joining(", ")));
                noteList.append("]");
            }
            String preview = note.getContent() != null && note.getContent().length() > 100
                    ? note.getContent().substring(0, 100) + "..."
                    : (note.getContent() != null ? note.getContent() : "");
            preview = preview.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
            if (!preview.isEmpty()) {
                noteList.append(" Preview: ").append(preview);
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

            if (result != null) {
                result = result.trim();
                if (result.startsWith("```json")) {
                    result = result.substring(7);
                } else if (result.startsWith("```")) {
                    result = result.substring(3);
                }
                if (result.endsWith("```")) {
                    result = result.substring(0, result.length() - 3);
                }
                result = result.trim();
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to analyze insights: {}", e.getMessage());
            return "{\"error\":\"\u6d1e\u5bdf\u5206\u6790\u5931\u8d25: " + sanitizeJson(e.getMessage()) + "\"}";
        }
    }

    public List<Map<String, Object>> findDuplicateCandidates(String userId) {
        List<NoteConcept> concepts = conceptRepository.findByUserId(userId);

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

                Set<String> intersection = new HashSet<>(set1);
                intersection.retainAll(set2);
                Set<String> union = new HashSet<>(set1);
                union.addAll(set2);

                if (!union.isEmpty()) {
                    double similarity = (double) intersection.size() / union.size();
                    if (similarity >= 0.5) {
                        Optional<Note> note1 = noteRepository.findById(noteIds.get(i));
                        Optional<Note> note2 = noteRepository.findById(noteIds.get(j));
                        if (note1.isPresent() && note2.isPresent()) {
                            Map<String, Object> candidate = new LinkedHashMap<>();
                            candidate.put("note1", Map.of(
                                    "id", note1.get().getId(),
                                    "title", note1.get().getTitle()));
                            candidate.put("note2", Map.of(
                                    "id", note2.get().getId(),
                                    "title", note2.get().getTitle()));
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

    public List<Map<String, Object>> getActivityTimeline(String userId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm");

        List<Map<String, Object>> timeline = new ArrayList<>();

        List<Note> recentNotes = noteRepository.findByUserIdAndDeletedAtIsNull(userId).stream()
                .filter(note -> note.getUpdatedAt().isAfter(since))
                .sorted((left, right) -> right.getUpdatedAt().compareTo(left.getUpdatedAt()))
                .limit(20)
                .toList();

        for (Note note : recentNotes) {
            Map<String, Object> event = new HashMap<>();
            event.put("time", note.getUpdatedAt().format(fmt));
            event.put("type", note.getCreatedAt().equals(note.getUpdatedAt()) ? "created" : "updated");
            event.put("title", note.getTitle());
            timeline.add(event);
        }

        List<Schedule> recentSchedules = scheduleRepository.findByUserIdOrderByStartTimeDesc(userId).stream()
                .filter(schedule -> schedule.getCreatedAt().isAfter(since))
                .limit(10)
                .toList();

        for (Schedule schedule : recentSchedules) {
            Map<String, Object> event = new HashMap<>();
            event.put("time", schedule.getCreatedAt().format(fmt));
            event.put("type", "schedule");
            event.put("title", schedule.getTitle());
            event.put("status", schedule.getStatus());
            timeline.add(event);
        }

        timeline.sort((left, right) -> ((String) right.get("time")).compareTo((String) left.get("time")));
        return timeline.stream().limit(20).toList();
    }

    private String sanitizeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
