package com.ainote.ai.agent.tools;

import com.ainote.ai.agent.pipeline.ToolExecutionPipeline;
import com.ainote.ai.feign.NoteClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 笔记复合工具（微服务版）
 * 通过 Feign 客户端调用 note-service，所有调用经 ToolExecutionPipeline 管道
 */
@Component
public class NoteActionTool {

    private static final Logger log = LoggerFactory.getLogger(NoteActionTool.class);
    private final NoteClient noteClient;
    private final ObjectMapper objectMapper;
    private final ToolExecutionPipeline pipeline;

    public NoteActionTool(NoteClient noteClient, ToolExecutionPipeline pipeline) {
        this.noteClient = noteClient;
        this.objectMapper = new ObjectMapper();
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
        - emptyTrash: {} → 返回确认请求
        - confirmEmptyTrash: {}
        - listTrash: {}
        """)
    public String noteAction(
            @P("操作类型") String action,
            @P("参数 JSON，如 {\"title\":\"标题\",\"content\":\"内容\"}") String paramsJson
    ) {
        log.info("Tool: noteAction called with action='{}', params='{}'", action, paramsJson);
        try {
            JsonNode params = parseParams(paramsJson);
            // userId is not available directly in microservices tools; pipeline uses "system"
            return pipeline.execute("system", "noteAction", action, params,
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
            case "emptyTrash" -> doEmptyTrash();
            case "confirmEmptyTrash" -> doConfirmEmptyTrash();
            case "listTrash" -> doListTrash();
            default -> "未知操作: " + action;
        };
    }

    private JsonNode parseParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        String s = paramsJson.trim();
        if (s.startsWith("```")) {
            s = s.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
        }
        try {
            return objectMapper.readTree(s);
        } catch (Exception e) {
            log.warn("Failed to parse paramsJson='{}', using empty object.", paramsJson);
            return objectMapper.createObjectNode();
        }
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }

    private String doListAll() {
        Map<String, Object> result = noteClient.listNotes(0, 100);
        if (result.containsKey("error")) return "获取笔记列表失败：" + result.get("error");

        Object contentObj = result.get("content");
        if (!(contentObj instanceof List<?> list) || list.isEmpty()) return "你还没有任何笔记";

        StringBuilder sb = new StringBuilder();
        sb.append("共有 ").append(list.size()).append(" 条笔记：\n");
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) instanceof Map<?, ?> note) {
                sb.append(String.format("%d. 「%s」(ID: %s)\n", i + 1, note.get("title"), note.get("id")));
            }
        }
        return sb.toString();
    }

    private String doCreate(JsonNode params) {
        String title = params.path("title").asText("无标题");
        String content = params.path("content").asText("");

        Map<String, Object> request = new HashMap<>();
        request.put("title", title);
        request.put("content", content);
        request.put("tags", List.of());

        Map<String, Object> note = noteClient.createNote(request);
        if (note.containsKey("error")) return "创建笔记失败：" + note.get("error");
        return String.format("已创建笔记「%s」（ID: %s）。", note.get("title"), note.get("id"));
    }

    private String doSearch(JsonNode params) {
        String query = params.path("query").asText("");
        if (query.isEmpty()) return doListAll();

        List<Map<String, Object>> notes = noteClient.searchNotes(query);
        if (notes.isEmpty()) return "未找到与「" + query + "」相关的笔记";

        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(notes.size()).append(" 条相关笔记：\n");
        for (int i = 0; i < Math.min(notes.size(), 10); i++) {
            Map<String, Object> note = notes.get(i);
            sb.append(String.format("%d. 「%s」(ID: %s)\n", i + 1, note.get("title"), note.get("id")));
        }
        if (notes.size() > 10) {
            sb.append("... 还有 ").append(notes.size() - 10).append(" 条结果");
        }
        return sb.toString();
    }

    private String doUpdate(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        if (noteId.isEmpty()) return "缺少 noteId";

        Map<String, Object> existing = noteClient.getNote(noteId);
        if (existing.containsKey("error")) return "未找到ID为「" + noteId + "」的笔记";

        Map<String, Object> request = new HashMap<>();
        request.put("title", params.has("title") && !params.get("title").isNull()
                ? params.get("title").asText() : existing.get("title"));
        request.put("content", params.has("content") && !params.get("content").isNull()
                ? params.get("content").asText() : existing.get("content"));

        Map<String, Object> updated = noteClient.updateNote(noteId, request);
        if (updated.containsKey("error")) return "更新失败";
        return String.format("已更新笔记「%s」。", updated.get("title"));
    }

    private String doDelete(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        if (noteId.isEmpty()) return "缺少 noteId";

        Map<String, Object> note = noteClient.getNote(noteId);
        if (note.containsKey("error")) return "未找到ID为「" + noteId + "」的笔记";

        String noteTitle = str(note, "title");
        return String.format("PENDING_ACTION:{\"type\":\"DELETE_NOTE\",\"noteId\":\"%s\",\"title\":\"%s\"}\n确认要删除笔记「%s」吗？（删除后可从回收站恢复）", noteId, noteTitle, noteTitle);
    }

    private String doConfirmDelete(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        if (noteId.isEmpty()) return "缺少 noteId";
        noteClient.deleteNote(noteId);
        return "已将笔记移入回收站";
    }

    private String doPermanentDelete(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        if (noteId.isEmpty()) return "缺少 noteId";

        Map<String, Object> note = noteClient.getNote(noteId);
        if (note.containsKey("error")) return "未找到该笔记";

        String noteTitle = str(note, "title");
        return String.format("PENDING_ACTION:{\"type\":\"PERMANENT_DELETE\",\"noteId\":\"%s\",\"title\":\"%s\"}\n确认要永久删除笔记「%s」吗？（不可恢复！）", noteId, noteTitle, noteTitle);
    }

    private String doConfirmPermanentDelete(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        if (noteId.isEmpty()) return "缺少 noteId";
        noteClient.permanentDeleteNote(noteId);
        return "笔记已永久删除";
    }

    private String doAddTag(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        String tagName = params.path("tagName").asText("");
        if (noteId.isEmpty()) return "缺少 noteId";
        if (tagName.isEmpty()) return "缺少 tagName";
        noteClient.addTag(noteId, Map.of("name", tagName));
        return String.format("已为笔记添加标签「%s」。", tagName);
    }

    private String doRemoveTag(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        String tagName = params.path("tagName").asText("");
        if (noteId.isEmpty()) return "缺少 noteId";
        if (tagName.isEmpty()) return "缺少 tagName";
        noteClient.removeTag(noteId, tagName);
        return String.format("已从笔记移除标签「%s」。", tagName);
    }

    private String doRestore(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        if (noteId.isEmpty()) return "缺少 noteId";
        noteClient.restoreNote(noteId);
        return "笔记已从回收站恢复。";
    }

    private String doMove(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        String folderId = params.has("folderId") && !params.get("folderId").isNull()
                ? params.get("folderId").asText() : null;
        if (noteId.isEmpty()) return "缺少 noteId";

        Map<String, Object> request = new HashMap<>();
        request.put("folderId", folderId);
        Map<String, Object> result = noteClient.moveNote(noteId, request);
        if (result.containsKey("error")) return "移动失败：" + result.get("error");

        String folderName = folderId == null ? "根目录" : "指定文件夹";
        return String.format("已将笔记移动到%s。", folderName);
    }

    private String doCopy(JsonNode params) {
        String noteId = params.path("noteId").asText("");
        if (noteId.isEmpty()) return "缺少 noteId";

        Map<String, Object> copy = noteClient.copyNote(noteId);
        if (copy.containsKey("error")) return "复制失败：" + copy.get("error");
        return String.format("已创建笔记副本「%s」（ID: %s）。", copy.get("title"), copy.get("id"));
    }

    private String doMerge(JsonNode params) {
        String noteIds = params.path("noteIds").asText("");
        String newTitle = params.path("title").asText("合并笔记");
        if (noteIds.isEmpty()) return "缺少 noteIds（逗号分隔的笔记ID列表）";

        List<String> ids = Arrays.stream(noteIds.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (ids.size() < 2) return "至少需要2条笔记才能合并";

        Map<String, Object> request = new HashMap<>();
        request.put("noteIds", ids);
        request.put("title", newTitle);
        Map<String, Object> merged = noteClient.mergeNotes(request);
        if (merged.containsKey("error")) return "合并失败";
        return String.format("已合并 %d 条笔记为「%s」（ID: %s）。", ids.size(), merged.get("title"), merged.get("id"));
    }

    private String doEmptyTrash() {
        List<Map<String, Object>> deleted = noteClient.listTrash();
        if (deleted.isEmpty()) return "回收站已经是空的，无需清空";
        return String.format("PENDING_ACTION:{\"type\":\"EMPTY_TRASH\",\"count\":\"%d\"}\n回收站中有 %d 条笔记，清空后将永久删除且不可恢复。确认清空吗？", deleted.size(), deleted.size());
    }

    private String doConfirmEmptyTrash() {
        noteClient.emptyTrash();
        return "已清空回收站";
    }

    private String doListTrash() {
        List<Map<String, Object>> deleted = noteClient.listTrash();
        if (deleted.isEmpty()) return "回收站是空的";

        StringBuilder sb = new StringBuilder();
        sb.append("回收站中有 ").append(deleted.size()).append(" 条笔记：\n");
        for (int i = 0; i < deleted.size(); i++) {
            Map<String, Object> note = deleted.get(i);
            sb.append(String.format("%d. 「%s」(ID: %s)\n", i + 1, note.get("title"), note.get("id")));
        }
        return sb.toString();
    }
}
