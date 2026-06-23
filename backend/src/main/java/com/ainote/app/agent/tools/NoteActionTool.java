package com.ainote.app.agent.tools;

import com.ainote.app.agent.pipeline.ToolExecutionPipeline;
import com.ainote.app.model.ClassificationResponse;
import com.ainote.app.model.ClassificationSuggestion;
import com.ainote.app.model.Note;
import com.ainote.app.model.NoteRequest;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.AiService;
import com.ainote.app.service.NoteService;
import com.ainote.app.service.NoteVersionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 笔记复合工具
 * 将 19 个细粒度笔记工具压缩为 1 个复合工具，减少 function schema token 消耗
 */
@Component
public class NoteActionTool {

    private static final Logger log = LoggerFactory.getLogger(NoteActionTool.class);
    private static final int DEDUP_WINDOW_SECONDS = 300;

    private final NoteService noteService;
    private final NoteRepository noteRepository;
    private final SecurityUtils securityUtils;
    private final AiService aiService;
    private final NoteVersionService noteVersionService;
    private final ObjectMapper objectMapper;
    private final ToolExecutionPipeline pipeline;

    public NoteActionTool(NoteService noteService, NoteRepository noteRepository,
                          SecurityUtils securityUtils, @Lazy AiService aiService,
                          NoteVersionService noteVersionService,
                          ToolExecutionPipeline pipeline,
                          ObjectMapper objectMapper) {
        this.noteService = noteService;
        this.noteRepository = noteRepository;
        this.securityUtils = securityUtils;
        this.aiService = aiService;
        this.noteVersionService = noteVersionService;
        this.objectMapper = objectMapper;
        this.pipeline = pipeline;
    }

    @Tool("""
        笔记操作复合工具。
        action 取值及参数：
        - create: {"title":"标题","content":"内容"}
        - search: {"query":"搜索关键词"}
        - listAll: {}
        - update: {"noteId":"ID","title":"新标题","content":"新内容"}
        - delete: {"noteId":"ID"} → 返回确认请求
        - confirmDelete: {"noteId":"ID"}
        - permanentDelete: {"noteId":"ID"} → 返回确认请求
        - confirmPermanentDelete: {"noteId":"ID"}
        - addTag: {"noteId":"ID","tagName":"标签名"}
        - removeTag: {"noteId":"ID","tagName":"标签名"}
        - restore: {"noteId":"ID"}
        - move: {"noteId":"ID","folderId":"文件夹ID"}
        - copy: {"noteId":"ID"}
        - merge: {"noteIds":"ID1,ID2,...","title":"合并后标题"}
        - versions: {"noteId":"ID"}
        - emptyTrash: {} → 返回确认请求
        - confirmEmptyTrash: {}
        - listTrash: {}
        - classify: {}
        - suggestTags: {"noteId":"ID"}
        """)
    public String noteAction(
            @P("操作类型") String action,
            @P("参数 JSON，如 {\"title\":\"标题\",\"content\":\"内容\"}") String paramsJson
    ) {
        log.info("Tool: noteAction called with action='{}', params='{}'", action, paramsJson);
        try {
            JsonNode params = parseParams(paramsJson);
            String userId = securityUtils.getCurrentUserId();

            return pipeline.execute(userId, "noteAction", action, params,
                    () -> doExecute(action, params));
        } catch (Exception e) {
            log.error("Tool: noteAction failed for action={}", action, e);
            return "笔记操作失败：" + e.getMessage();
        }
    }

    private String doExecute(String action, JsonNode params) {
        return switch (action) {
            case "create" -> doCreate(params);
            case "search" -> doSearch(params);
            case "listAll" -> doListAll();
            case "update" -> doUpdate(params);
            case "delete" -> doDelete(params);
            case "confirmDelete" -> doConfirmDelete(params);
            case "permanentDelete" -> doPermanentDelete(params);
            case "confirmPermanentDelete" -> doConfirmPermanentDelete(params);
            case "addTag" -> doAddTag(params);
            case "removeTag" -> doRemoveTag(params);
            case "restore" -> doRestore(params);
            case "move" -> doMove(params);
            case "copy" -> doCopy(params);
            case "merge" -> doMerge(params);
            case "versions" -> doVersions(params);
            case "emptyTrash" -> doEmptyTrash();
            case "confirmEmptyTrash" -> doConfirmEmptyTrash();
            case "listTrash" -> doListTrash();
            case "classify" -> doClassify();
            case "suggestTags" -> doSuggestTags(params);
            default -> "未知操作: " + action;
        };
    }

    /**
     * 安全解析 paramsJson，容忍 Agent 传入的各种异常格式
     */
    private JsonNode parseParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        String s = paramsJson.trim();
        // 去除 Agent 可能包裹的 markdown 代码块
        if (s.startsWith("```")) {
            s = s.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
        }
        try {
            return objectMapper.readTree(s);
        } catch (Exception e) {
            log.warn("Failed to parse paramsJson='{}', using empty object. Error: {}", paramsJson, e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private String doListAll() {
        String userId = securityUtils.getCurrentUserId();
        List<com.ainote.app.entity.Note> notes = noteRepository.findByUserIdWithTags(userId);
        if (notes.isEmpty()) return "你还没有任何笔记";

        StringBuilder sb = new StringBuilder();
        sb.append("共有 ").append(notes.size()).append(" 条笔记：\n");
        for (int i = 0; i < notes.size(); i++) {
            com.ainote.app.entity.Note note = notes.get(i);
            String tags = note.getTags() != null && !note.getTags().isEmpty()
                    ? note.getTags().stream().map(t -> t.getName()).collect(Collectors.joining(","))
                    : "";
            sb.append(String.format("%d. 「%s」(ID: %s)%s\n",
                    i + 1, note.getTitle(), note.getId(),
                    tags.isEmpty() ? "" : " [" + tags + "]"));
        }
        return sb.toString();
    }

    private String doCreate(JsonNode params) {
        String title = params.path("title").asText("无标题");
        String content = params.path("content").asText("");

        // 幂等保护：标题匹配 + 内容精确匹配，300s 窗口
        String userId = securityUtils.getCurrentUserId();
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(DEDUP_WINDOW_SECONDS);
        List<com.ainote.app.entity.Note> recent = noteRepository.findRecentByUserIdAndTitle(userId, title, cutoff);
        for (com.ainote.app.entity.Note existing : recent) {
            if (Objects.equals(existing.getTitle(), title) && Objects.equals(existing.getContent(), content)) {
                return String.format("该笔记已存在：「%s」（ID: %s），无需重复创建。", existing.getTitle(), existing.getId());
            }
        }
        if (!recent.isEmpty() && content.isEmpty()) {
            // 同名且无内容，也视为重复
            com.ainote.app.entity.Note existing = recent.get(0);
            return String.format("该笔记已存在：「%s」（ID: %s），无需重复创建。", existing.getTitle(), existing.getId());
        }

        NoteRequest request = new NoteRequest();
        request.setTitle(title);
        request.setContent(content);
        request.setTags(readStringArray(params, "tags"));
        if (params.has("folderId") && !params.get("folderId").isNull()) {
            request.setFolderId(params.get("folderId").asText());
        }

        Note note = noteService.create(request);
        return String.format("已创建笔记「%s」（ID: %s）。", note.getTitle(), note.getId());
    }

    private String doSearch(JsonNode params) {
        String query = params.path("query").asText("");
        if (query.isEmpty()) return doListAll();

        List<Note> notes = noteService.hybridSearch(query);
        if (notes.isEmpty()) return "未找到与「" + query + "」相关的笔记";

        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(notes.size()).append(" 条相关笔记：\n");
        for (int i = 0; i < Math.min(notes.size(), 10); i++) {
            Note note = notes.get(i);
            sb.append(String.format("%d. 「%s」(ID: %s)\n", i + 1, note.getTitle(), note.getId()));
        }
        if (notes.size() > 10) {
            sb.append("... 还有 ").append(notes.size() - 10).append(" 条结果");
        }
        return sb.toString();
    }

    private String doUpdate(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        if (noteId.isEmpty()) return "缺少 noteId";

        Optional<Note> existingOpt = noteService.getById(noteId);
        if (existingOpt.isEmpty()) return "未找到ID为「" + noteId + "」的笔记";

        Note existing = existingOpt.get();
        String newTitle = params.has("title") && !params.get("title").isNull() ? params.get("title").asText() : null;
        String newContent = params.has("content") && !params.get("content").isNull() ? params.get("content").asText() : null;

        NoteRequest request = new NoteRequest();
        request.setTitle(newTitle != null ? newTitle : existing.getTitle());
        request.setContent(newContent != null ? newContent : existing.getContent());
        request.setTags(existing.getTags() != null
                ? existing.getTags().stream().map(t -> t.getName()).toList()
                : List.of());
        request.setFolderId(existing.getFolderId());

        Optional<Note> updatedOpt = noteService.update(noteId, request);
        return updatedOpt.isPresent()
                ? String.format("已更新笔记「%s」。", updatedOpt.get().getTitle())
                : "更新失败";
    }

    private String doDelete(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        if (noteId.isEmpty()) return "缺少 noteId";

        Optional<Note> noteOpt = noteService.getById(noteId);
        if (noteOpt.isEmpty()) return "未找到ID为「" + noteId + "」的笔记";

        String noteTitle = noteOpt.get().getTitle();
        return String.format("PENDING_ACTION:{\"type\":\"DELETE_NOTE\",\"noteId\":\"%s\",\"title\":\"%s\"}\n⚠️ 确认要删除笔记「%s」吗？（删除后可从回收站恢复）", noteId, noteTitle, noteTitle);
    }

    private String doConfirmDelete(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        if (noteId.isEmpty()) return "缺少 noteId";

        Optional<Note> noteOpt = noteService.getById(noteId);
        String noteTitle = noteOpt.map(Note::getTitle).orElse("未知");
        noteService.delete(noteId);
        return String.format("✅ 已将笔记「%s」移入回收站", noteTitle);
    }

    private String doPermanentDelete(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        if (noteId.isEmpty()) return "缺少 noteId";

        Optional<Note> noteOpt = noteService.getById(noteId);
        if (noteOpt.isEmpty()) return "未找到该笔记";

        String noteTitle = noteOpt.get().getTitle();
        return String.format("PENDING_ACTION:{\"type\":\"PERMANENT_DELETE\",\"noteId\":\"%s\",\"title\":\"%s\"}\n🔴 确认要永久删除笔记「%s」吗？（不可恢复！）", noteId, noteTitle, noteTitle);
    }

    private String doConfirmPermanentDelete(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        if (noteId.isEmpty()) return "缺少 noteId";

        noteService.permanentDelete(noteId);
        return "✅ 笔记已永久删除";
    }

    private static final int OPTIMISTIC_LOCK_MAX_RETRIES = 3;

    private String doAddTag(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        String tagName = params.path("tagName").asText("");
        if (noteId.isEmpty()) return "缺少 noteId";
        List<String> tagNames = tagName.isEmpty() ? readStringArray(params, "tags") : List.of(tagName);
        if (tagNames.isEmpty()) return "缺少 tagName";

        List<String> results = new java.util.ArrayList<>();
        for (String tag : tagNames) {
            results.add(addSingleTag(noteId, tag));
        }
        return String.join("\n", results);
    }

    private String addSingleTag(String noteId, String tagName) {
        for (int attempt = 0; attempt < OPTIMISTIC_LOCK_MAX_RETRIES; attempt++) {
            Optional<Note> noteOpt = noteService.getById(noteId);
            if (noteOpt.isEmpty()) return "未找到ID为「" + noteId + "」的笔记";

            Note note = noteOpt.get();
            List<String> tags = note.getTags() != null
                    ? note.getTags().stream().map(t -> t.getName()).collect(Collectors.toList())
                    : new java.util.ArrayList<>();

            if (tags.contains(tagName)) {
                return String.format("笔记「%s」已有标签「%s」", note.getTitle(), tagName);
            }

            tags.add(tagName);
            NoteRequest request = new NoteRequest();
            request.setTitle(note.getTitle());
            request.setContent(note.getContent());
            request.setTags(tags);
            request.setFolderId(note.getFolderId());
            try {
                noteService.update(noteId, request);
                return String.format("已为笔记「%s」添加标签「%s」。", note.getTitle(), tagName);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("Optimistic lock conflict on addTag, attempt {}/{}", attempt + 1, OPTIMISTIC_LOCK_MAX_RETRIES);
                if (attempt == OPTIMISTIC_LOCK_MAX_RETRIES - 1) {
                    return "添加标签失败：笔记正在被其他操作修改，请稍后重试";
                }
            }
        }
        return "添加标签失败：重试次数耗尽";
    }

    private List<String> readStringArray(JsonNode params, String field) {
        JsonNode node = params.path(field);
        if (!node.isArray()) return List.of();
        List<String> values = new java.util.ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("");
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private String doRemoveTag(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        String tagName = params.path("tagName").asText("");
        if (noteId.isEmpty()) return "缺少 noteId";
        if (tagName.isEmpty()) return "缺少 tagName";

        for (int attempt = 0; attempt < OPTIMISTIC_LOCK_MAX_RETRIES; attempt++) {
            Optional<Note> noteOpt = noteService.getById(noteId);
            if (noteOpt.isEmpty()) return "未找到ID为「" + noteId + "」的笔记";

            Note note = noteOpt.get();
            List<String> tags = note.getTags() != null
                    ? note.getTags().stream().map(t -> t.getName()).collect(Collectors.toList())
                    : new java.util.ArrayList<>();

            if (!tags.contains(tagName)) {
                return String.format("笔记「%s」没有标签「%s」", note.getTitle(), tagName);
            }

            tags.remove(tagName);
            NoteRequest request = new NoteRequest();
            request.setTitle(note.getTitle());
            request.setContent(note.getContent());
            request.setTags(tags);
            request.setFolderId(note.getFolderId());
            try {
                noteService.update(noteId, request);
                return String.format("已从笔记「%s」移除标签「%s」。", note.getTitle(), tagName);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("Optimistic lock conflict on removeTag, attempt {}/{}", attempt + 1, OPTIMISTIC_LOCK_MAX_RETRIES);
                if (attempt == OPTIMISTIC_LOCK_MAX_RETRIES - 1) {
                    return "移除标签失败：笔记正在被其他操作修改，请稍后重试";
                }
            }
        }
        return "移除标签失败：重试次数耗尽";
    }

    private String doRestore(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        if (noteId.isEmpty()) return "缺少 noteId";

        noteService.restore(noteId);
        return "笔记已从回收站恢复。";
    }

    private String doMove(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        String folderId = params.has("folderId") && !params.get("folderId").isNull()
                ? params.get("folderId").asText() : null;
        if (noteId.isEmpty()) return "缺少 noteId";

        Optional<Note> result = noteService.moveToFolder(noteId, folderId);
        if (result.isPresent()) {
            String folderName = folderId == null ? "根目录" : "指定文件夹";
            return String.format("已将笔记「%s」移动到%s。", result.get().getTitle(), folderName);
        }
        return "移动失败：笔记或文件夹不存在";
    }

    private String doCopy(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        if (noteId.isEmpty()) return "缺少 noteId";

        Note copy = noteService.copy(noteId);
        if (copy != null) {
            return String.format("已创建笔记副本「%s」（ID: %s）。", copy.getTitle(), copy.getId());
        }
        return "复制失败：笔记不存在";
    }

    private String doMerge(JsonNode params) {
        String noteIds = params.path("noteIds").asText("");
        String newTitle = params.path("title").asText("合并笔记");
        if (noteIds.isEmpty()) return "缺少 noteIds（逗号分隔的笔记ID列表）";

        List<String> ids = Arrays.stream(noteIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (ids.size() < 2) return "至少需要2条笔记才能合并";

        Note merged = noteService.merge(ids, newTitle);
        if (merged != null) {
            return String.format("已合并 %d 条笔记为「%s」（ID: %s）。", ids.size(), merged.getTitle(), merged.getId());
        }
        return "合并失败";
    }

    private String doEmptyTrash() {
        List<Note> deleted = noteService.listDeleted();
        if (deleted.isEmpty()) return "回收站已经是空的，无需清空";
        return String.format("PENDING_ACTION:{\"type\":\"EMPTY_TRASH\",\"count\":\"%d\"}\n🔴 回收站中有 %d 条笔记，清空后将永久删除且不可恢复。确认清空吗？", deleted.size(), deleted.size());
    }

    private String doConfirmEmptyTrash() {
        int count = noteService.emptyTrash();
        return String.format("✅ 已清空回收站，永久删除了 %d 条笔记", count);
    }

    private String doListTrash() {
        List<Note> deleted = noteService.listDeleted();
        if (deleted.isEmpty()) return "回收站是空的";

        StringBuilder sb = new StringBuilder();
        sb.append("回收站中有 ").append(deleted.size()).append(" 条笔记：\n");
        for (int i = 0; i < deleted.size(); i++) {
            Note note = deleted.get(i);
            sb.append(String.format("%d. 「%s」(ID: %s)\n", i + 1, note.getTitle(), note.getId()));
        }
        return sb.toString();
    }

    private String doVersions(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        if (noteId.isEmpty()) return "缺少 noteId";
        if (noteService.getById(noteId).isEmpty()) return "笔记不存在或无权访问";

        List<java.util.Map<String, Object>> versions = noteVersionService.getVersions(noteId);
        if (versions.isEmpty()) return "该笔记暂无历史版本";

        StringBuilder sb = new StringBuilder();
        sb.append("该笔记共有 ").append(versions.size()).append(" 个历史版本：\n");
        for (int i = 0; i < versions.size(); i++) {
            java.util.Map<String, Object> version = versions.get(i);
            String content = String.valueOf(version.getOrDefault("content", ""));
            String preview = content.length() > 80 ? content.substring(0, 80) + "..." : content;
            sb.append(String.format("%d. %s：%s\n", i + 1, version.get("createdAt"), preview));
        }
        return sb.toString();
    }

    private String doClassify() {
        ClassificationResponse response = aiService.classifyNotes();

        if (response.getSuggestions() == null || response.getSuggestions().isEmpty()) {
            return response.getSummary() != null ? response.getSummary() : "没有需要分类的笔记";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📁 智能分类建议：\n\n");

        if (response.getNewFolders() != null && !response.getNewFolders().isEmpty()) {
            sb.append("需要新建的文件夹：\n");
            for (String folder : response.getNewFolders()) {
                sb.append("  • ").append(folder).append("\n");
            }
            sb.append("\n");
        }

        sb.append("分类建议：\n");
        for (int i = 0; i < response.getSuggestions().size(); i++) {
            ClassificationSuggestion s = response.getSuggestions().get(i);
            sb.append(String.format("%d. 「%s」→ %s%s\n",
                    i + 1, s.getNoteTitle(), s.getSuggestedFolderName(),
                    s.isNewFolder() ? "（新建）" : ""));
            if (s.getReason() != null && !s.getReason().isEmpty()) {
                sb.append("   原因：").append(s.getReason()).append("\n");
            }
        }

        if (response.getSummary() != null) {
            sb.append("\n").append(response.getSummary());
        }
        return sb.toString();
    }

    private String doSuggestTags(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        if (noteId.isEmpty()) return "缺少 noteId";
        return aiService.spiritSuggestTags(noteId);
    }
}
