package com.ainote.app.service;

import com.ainote.app.entity.UserMemory;
import com.ainote.app.model.AiChatResponse;
import com.ainote.app.model.ChatHistoryItem;
import com.ainote.app.model.ClassificationResponse;
import com.ainote.app.model.ClassificationSuggestion;
import com.ainote.app.model.ExtractedSchedule;
import com.ainote.app.model.Folder;
import com.ainote.app.model.Note;
import com.ainote.app.repository.UserMemoryRepository;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.util.PromptLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final NoteService noteService;
    private final FolderService folderService;
    private final ChatModel chatModel;
    private final SecurityUtils securityUtils;
    private final UserMemoryRepository userMemoryRepository;
    private final ObjectMapper objectMapper;
    private final PromptLoader promptLoader;
    private static final DateTimeFormatter SCHEDULE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final Pattern ENGLISH_EXPLICIT_SCHEDULE = Pattern.compile(
            "(?i)\\b(?:schedule|plan|arrange|set up)?\\s*(?:a|an|the)?\\s*"
                    + "([A-Za-z][A-Za-z0-9 ,'-]{2,120}?)\\s+on\\s+"
                    + "(January|February|March|April|May|June|July|August|September|October|November|December)\\s+"
                    + "(\\d{1,2}),\\s*(\\d{4})\\s+at\\s+"
                    + "(\\d{1,2})(?::(\\d{2}))?\\s*(AM|PM)?"
                    + "(?:\\s+for\\s+(\\d+)\\s*(minute|minutes|hour|hours))?");

    public AiService(
            NoteService noteService,
            FolderService folderService,
            @Qualifier("agentChatModel") ChatModel chatModel,
            SecurityUtils securityUtils,
            UserMemoryRepository userMemoryRepository,
            PromptLoader promptLoader,
            ObjectMapper objectMapper) {
        this.noteService = noteService;
        this.folderService = folderService;
        this.chatModel = chatModel;
        this.securityUtils = securityUtils;
        this.userMemoryRepository = userMemoryRepository;
        this.objectMapper = objectMapper;
        this.promptLoader = promptLoader;
    }

    public void saveChatMessage(String userId, String role, String content) {
        try {
            UserMemory msg = new UserMemory();
            msg.setUserId(userId);
            msg.setMessageType(role.equals("user") ? "USER" : "AI");
            msg.setContent(content);
            msg.setCreatedAt(LocalDateTime.now());
            userMemoryRepository.save(msg);
        } catch (Exception e) {
            log.error("Failed to save chat message [userId={}, role={}]: {}", userId, role, e.getMessage());
        }
    }

    public List<ChatHistoryItem> getChatHistory(String userId, int limit) {
        return userMemoryRepository
                .findByUserIdOrderByCreatedAtAsc(userId, PageRequest.of(0, limit))
                .stream()
                .filter(m -> "USER".equals(m.getMessageType()) || "AI".equals(m.getMessageType()))
                .map(m -> new ChatHistoryItem(
                        m.getId().toString(),
                        "USER".equals(m.getMessageType()) ? "user" : "assistant",
                        m.getContent(),
                        m.getCreatedAt()
                ))
                .toList();
    }

    public void clearChatMemory(String userId) {
        log.info("Clearing chat memory for user: {}", userId);
        userMemoryRepository.deleteByUserId(userId);
    }

    public String spiritGreeting() {
        String[] greetings = {
                "Hello! I'm your AI assistant. How can I help you today?",
                "Welcome back. What would you like to capture today?",
                "Hi. Ready to organize your thoughts?",
                "What would you like to work on?"
        };
        int index = (int) (Math.random() * greetings.length);
        return greetings[index];
    }

    public String spiritSuggestTags(String noteId) {
        Note note = noteService.getById(noteId)
                .orElseThrow(() -> new RuntimeException("Note not found"));
        String prompt = promptLoader.format("suggest-tags.txt", note.getTitle(), note.getContent());
        return callChatModel(prompt, List.of());
    }

    public ClassificationResponse classifyNotes() {
        List<Note> notes = noteService.listAll();
        List<Folder> folders = folderService.listAll();
        if (notes.isEmpty()) {
            return new ClassificationResponse(List.of(), List.of(), "No notes to classify");
        }

        String prompt = promptLoader.format("classify-notes.txt", buildFoldersInfo(folders), buildNotesInfo(notes));
        String aiResponse = callChatModel(prompt, List.of());
        try {
            JsonNode root = objectMapper.readTree(extractJsonObject(aiResponse));
            List<ClassificationSuggestion> suggestions = new ArrayList<>();
            JsonNode suggestionsNode = root.get("suggestions");
            if (suggestionsNode != null && suggestionsNode.isArray()) {
                for (JsonNode node : suggestionsNode) {
                    ClassificationSuggestion suggestion = new ClassificationSuggestion();
                    suggestion.setNoteId(node.has("noteId") ? node.get("noteId").asText() : null);
                    suggestion.setNoteTitle(node.has("noteTitle") ? node.get("noteTitle").asText() : null);
                    suggestion.setSuggestedFolderId(node.has("suggestedFolderId") && !node.get("suggestedFolderId").isNull()
                            ? node.get("suggestedFolderId").asText() : null);
                    suggestion.setSuggestedFolderName(node.has("suggestedFolderName") ? node.get("suggestedFolderName").asText() : null);
                    suggestion.setReason(node.has("reason") ? node.get("reason").asText() : null);
                    suggestion.setNewFolder(node.has("isNewFolder") && node.get("isNewFolder").asBoolean());
                    suggestions.add(suggestion);
                }
            }

            List<String> newFolders = new ArrayList<>();
            JsonNode newFoldersNode = root.get("newFolders");
            if (newFoldersNode != null && newFoldersNode.isArray()) {
                for (JsonNode node : newFoldersNode) {
                    newFolders.add(node.asText());
                }
            }
            String summary = root.has("summary") ? root.get("summary").asText() : "Classification complete";
            return new ClassificationResponse(suggestions, newFolders, summary);
        } catch (Exception e) {
            log.error("Failed to parse classification response: {}", e.getMessage());
            return new ClassificationResponse(List.of(), List.of(), "Classification failed: " + e.getMessage());
        }
    }

    public ExtractedSchedule.ExtractResponse extractSchedules(String noteId) {
        Optional<Note> noteOpt = noteService.getById(noteId);
        if (noteOpt.isEmpty()) {
            return new ExtractedSchedule.ExtractResponse(List.of(), noteId);
        }

        Note note = noteOpt.get();
        String plainText = htmlToPlainText(note.getContent());
        if (plainText.isBlank()) {
            return new ExtractedSchedule.ExtractResponse(List.of(), noteId);
        }

        LocalDateTime now = LocalDateTime.now();
        String prompt = promptLoader.format(
                "extract-schedules.txt",
                now.toLocalDate().toString(),
                now.getDayOfWeek().toString(),
                plainText
        );

        try {
            String response = callChatModel(prompt, List.of());
            JsonNode root = objectMapper.readTree(extractJsonObject(response));
            JsonNode schedulesNode = root.get("schedules");
            List<ExtractedSchedule> schedules = new ArrayList<>();
            if (schedulesNode != null && schedulesNode.isArray()) {
                for (JsonNode node : schedulesNode) {
                    double confidence = node.has("confidence") ? node.get("confidence").asDouble() : 0.8;
                    if (confidence < 0.6) {
                        continue;
                    }
                    ExtractedSchedule schedule = parseScheduleNode(node);
                    if (schedule != null) {
                        schedules.add(schedule);
                    }
                }
            }
            if (!schedules.isEmpty()) {
                return new ExtractedSchedule.ExtractResponse(schedules, noteId);
            }
            return new ExtractedSchedule.ExtractResponse(parseExplicitScheduleFallback(plainText), noteId);
        } catch (Exception e) {
            log.error("Failed to extract schedules: {}", e.getMessage(), e);
            return new ExtractedSchedule.ExtractResponse(parseExplicitScheduleFallback(plainText), noteId);
        }
    }

    private List<UserMemory> recentHistory(String userId) {
        return userMemoryRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 10))
                .stream()
                .sorted(Comparator.comparing(UserMemory::getCreatedAt))
                .toList();
    }

    private String callChatModel(String prompt, List<UserMemory> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from("You are AI Note's assistant. Answer concisely and use the provided context when present."));
        for (UserMemory h : history) {
            if ("USER".equals(h.getMessageType())) {
                messages.add(UserMessage.from(h.getContent()));
            } else if ("AI".equals(h.getMessageType())) {
                messages.add(AiMessage.from(h.getContent()));
            }
        }
        messages.add(UserMessage.from(prompt));
        ChatResponse response = chatModel.chat(ChatRequest.builder().messages(messages).build());
        return response.aiMessage() != null && response.aiMessage().text() != null
                ? response.aiMessage().text()
                : "";
    }

    private List<Note> getNotes(String query, List<String> noteIds) {
        if (noteIds != null && !noteIds.isEmpty()) {
            return noteIds.stream()
                    .map(id -> noteService.getById(id).orElse(null))
                    .filter(n -> n != null)
                    .toList();
        }
        if (isRestoreOperationQuery(query)) {
            List<Note> trashNotes = noteService.listDeleted();
            if (isBatchOperationQuery(query)) {
                return trashNotes.stream().map(this::simplifyNote).toList();
            }
            List<Note> matched = trashNotes.stream()
                    .filter(note -> matchesRestoreQuery(note, query))
                    .map(this::simplifyNote)
                    .toList();
            return matched.isEmpty() ? trashNotes.stream().map(this::simplifyNote).toList() : matched;
        }
        if (isBatchOperationQuery(query)) {
            return noteService.listAll().stream().map(this::simplifyNote).toList();
        }
        if (isNoteRelatedQuery(query)) {
            List<Note> notes = noteService.hybridSearch(query);
            return notes.isEmpty() ? noteService.listAll() : notes;
        }
        return List.of();
    }

    private boolean isNoteRelatedQuery(String query) {
        if (query == null) {
            return false;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return q.contains("note")
                || q.contains("notes")
                || q.contains("笔记")
                || q.contains("记录")
                || q.contains("内容")
                || q.contains("总结")
                || q.contains("整理");
    }

    private boolean isBatchOperationQuery(String query) {
        if (query == null) {
            return false;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return q.contains("all") || q.contains("batch") || q.contains("所有") || q.contains("全部") || q.contains("批量");
    }

    private boolean isRestoreOperationQuery(String query) {
        if (query == null) {
            return false;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return q.contains("restore") || q.contains("recover") || q.contains("恢复") || q.contains("还原") || q.contains("找回");
    }

    private Note simplifyNote(Note note) {
        Note simplified = new Note();
        simplified.setId(note.getId());
        simplified.setTitle(note.getTitle());
        simplified.setContent("");
        return simplified;
    }

    private boolean matchesRestoreQuery(Note note, String query) {
        String title = note.getTitle() != null ? note.getTitle().toLowerCase(Locale.ROOT) : "";
        String content = note.getContent() != null ? note.getContent().toLowerCase(Locale.ROOT) : "";
        String searchTerm = query.toLowerCase(Locale.ROOT)
                .replace("restore", "")
                .replace("recover", "")
                .replace("恢复", "")
                .replace("还原", "")
                .replace("找回", "")
                .trim();
        return searchTerm.isBlank() || title.contains(searchTerm) || content.contains(searchTerm);
    }

    private Map<Integer, AiChatResponse.NoteSource> buildSources(List<Note> notes) {
        Map<Integer, AiChatResponse.NoteSource> sources = new HashMap<>();
        int index = 1;
        for (Note note : notes) {
            sources.put(index, new AiChatResponse.NoteSource(note.getId(), note.getTitle()));
            index++;
        }
        return sources;
    }

    private String buildNotesInfo(List<Note> notes) {
        StringBuilder notesInfo = new StringBuilder();
        for (int i = 0; i < notes.size(); i++) {
            Note note = notes.get(i);
            String content = note.getContent();
            if (content != null && content.length() > 200) {
                content = content.substring(0, 200) + "...";
            }
            notesInfo.append(String.format(
                    "[Note %d] ID: %s\nTitle: %s\nSummary: %s\nCurrent folder: %s\n\n",
                    i + 1,
                    note.getId(),
                    note.getTitle(),
                    content != null ? content : "(empty)",
                    note.getFolderId() != null ? note.getFolderId() : "unfiled"
            ));
        }
        return notesInfo.toString();
    }

    private String buildFoldersInfo(List<Folder> folders) {
        if (folders.isEmpty()) {
            return "(no folders)";
        }
        StringBuilder foldersInfo = new StringBuilder();
        for (Folder folder : folders) {
            foldersInfo.append("- ").append(folder.getName()).append(" (ID: ").append(folder.getId()).append(")\n");
        }
        return foldersInfo.toString();
    }

    private String extractJsonObject(String response) {
        String json = response == null ? "" : response.trim();
        if (json.startsWith("```json")) {
            json = json.substring(7);
        } else if (json.startsWith("```")) {
            json = json.substring(3);
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        json = json.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        return start >= 0 && end > start ? json.substring(start, end + 1) : json;
    }

    private ExtractedSchedule parseScheduleNode(JsonNode node) {
        String title = node.has("title") ? node.get("title").asText() : null;
        String startTime = node.has("startTime") ? node.get("startTime").asText() : null;
        if (title == null || startTime == null) {
            return null;
        }
        ExtractedSchedule schedule = new ExtractedSchedule();
        schedule.setTitle(title);
        schedule.setStartTime(startTime);
        schedule.setEndTime(node.has("endTime") && !node.get("endTime").isNull() ? node.get("endTime").asText() : null);
        schedule.setAllDay(node.has("allDay") && node.get("allDay").asBoolean());
        schedule.setRrule(node.has("rrule") && !node.get("rrule").isNull() ? node.get("rrule").asText() : null);
        schedule.setConfidence(node.has("confidence") ? node.get("confidence").asDouble() : 0.9);
        schedule.setSource(node.has("source") ? node.get("source").asText() : title);
        return schedule;
    }

    private List<ExtractedSchedule> parseExplicitScheduleFallback(String plainText) {
        Matcher matcher = ENGLISH_EXPLICIT_SCHEDULE.matcher(plainText);
        List<ExtractedSchedule> schedules = new ArrayList<>();
        while (matcher.find()) {
            Month month = englishMonth(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            int year = Integer.parseInt(matcher.group(4));
            int hour = Integer.parseInt(matcher.group(5));
            int minute = matcher.group(6) != null ? Integer.parseInt(matcher.group(6)) : 0;
            String meridiem = matcher.group(7);
            if (meridiem != null) {
                if ("PM".equalsIgnoreCase(meridiem) && hour < 12) {
                    hour += 12;
                } else if ("AM".equalsIgnoreCase(meridiem) && hour == 12) {
                    hour = 0;
                }
            }

            int durationMinutes = durationMinutes(matcher.group(8), matcher.group(9));
            LocalDateTime start = LocalDateTime.of(
                    LocalDate.of(year, month, day),
                    LocalTime.of(hour, minute));
            LocalDateTime end = start.plusMinutes(durationMinutes);
            String source = sourceSentence(plainText, matcher.start(), matcher.end());

            ExtractedSchedule schedule = new ExtractedSchedule();
            schedule.setTitle(cleanFallbackTitle(matcher.group(1)));
            schedule.setStartTime(start.format(SCHEDULE_TIME_FORMAT));
            schedule.setEndTime(end.format(SCHEDULE_TIME_FORMAT));
            schedule.setAllDay(false);
            schedule.setRrule(null);
            schedule.setConfidence(0.9);
            schedule.setSource(source);
            schedules.add(schedule);
        }
        return schedules;
    }

    private static Month englishMonth(String month) {
        return switch (month.toLowerCase(Locale.ROOT)) {
            case "january" -> Month.JANUARY;
            case "february" -> Month.FEBRUARY;
            case "march" -> Month.MARCH;
            case "april" -> Month.APRIL;
            case "may" -> Month.MAY;
            case "june" -> Month.JUNE;
            case "july" -> Month.JULY;
            case "august" -> Month.AUGUST;
            case "september" -> Month.SEPTEMBER;
            case "october" -> Month.OCTOBER;
            case "november" -> Month.NOVEMBER;
            case "december" -> Month.DECEMBER;
            default -> throw new IllegalArgumentException("Unsupported month: " + month);
        };
    }

    private static int durationMinutes(String amount, String unit) {
        if (amount == null || unit == null) {
            return 60;
        }
        int value = Integer.parseInt(amount);
        return unit.toLowerCase(Locale.ROOT).startsWith("hour") ? value * 60 : value;
    }

    private static String cleanFallbackTitle(String rawTitle) {
        String title = rawTitle == null ? "" : rawTitle.replaceAll("\\s+", " ").trim();
        title = title.replaceAll("(?i)\\s+with\\s+the\\s+.+$", "").trim();
        return title.isBlank() ? "Scheduled item" : title;
    }

    private static String sourceSentence(String text, int start, int end) {
        int left = Math.max(text.lastIndexOf('.', start), text.lastIndexOf('\n', start));
        int rightPeriod = text.indexOf('.', end);
        int rightBreak = text.indexOf('\n', end);
        int right;
        if (rightPeriod < 0) {
            right = rightBreak < 0 ? text.length() : rightBreak;
        } else if (rightBreak < 0) {
            right = rightPeriod;
        } else {
            right = Math.min(rightPeriod, rightBreak);
        }
        return text.substring(left + 1, right).trim();
    }

    private String htmlToPlainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return html
                .replaceAll("<br\\s*/?>", "\n")
                .replaceAll("</p>", "\n")
                .replaceAll("</div>", "\n")
                .replaceAll("</li>", "\n")
                .replaceAll("</h[1-6]>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}
