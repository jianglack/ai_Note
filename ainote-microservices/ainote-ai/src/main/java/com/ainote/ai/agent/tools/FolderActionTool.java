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
 * 文件夹复合工具（微服务版）
 * 通过 Feign 客户端调用 note-service，所有调用经 ToolExecutionPipeline 管道
 */
@Component
public class FolderActionTool {

    private static final Logger log = LoggerFactory.getLogger(FolderActionTool.class);
    private final NoteClient noteClient;
    private final ObjectMapper objectMapper;
    private final ToolExecutionPipeline pipeline;

    public FolderActionTool(NoteClient noteClient, ToolExecutionPipeline pipeline) {
        this.noteClient = noteClient;
        this.objectMapper = new ObjectMapper();
        this.pipeline = pipeline;
    }

    @Tool("""
        文件夹操作复合工具。
        action 取值及参数：
        - create: {"name":"文件夹名"} 可选: parentId(父文件夹ID)。有幂等保护，同名不会重复创建
        - list: {} 列出当前用户所有文件夹
        - rename: {"folderId":"ID","name":"新名称"}
        - delete: {"folderId":"ID"} → 返回确认请求，需用户确认后调用 confirmDelete
        - confirmDelete: {"folderId":"ID"} 用户确认后执行删除，文件夹内笔记会移到根目录
        """)
    public String folderAction(
            @P("操作类型") String action,
            @P("参数 JSON，如 {\"name\":\"工作\"}") String paramsJson
    ) {
        log.info("Tool: folderAction called with action='{}', params='{}'", action, paramsJson);
        try {
            JsonNode params = parseParams(paramsJson);
            return pipeline.execute("system", "folderAction", action, params,
                    () -> doExecute(action, params));
        } catch (Exception e) {
            log.error("Tool: folderAction failed for action={}", action, e);
            return "文件夹操作失败：" + e.getMessage();
        }
    }

    private String doExecute(String action, JsonNode params) {
        return switch (action) {
            case "create" -> doCreate(params);
            case "list" -> doList();
            case "rename" -> doRename(params);
            case "delete" -> doDelete(params);
            case "confirmDelete" -> doConfirmDelete(params);
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

    private String doCreate(JsonNode params) {
        String name = params.path("name").asText("");
        if (name.isEmpty()) return "缺少 name";

        Map<String, Object> request = new HashMap<>();
        request.put("name", name);
        if (params.has("parentId") && !params.get("parentId").isNull()) {
            request.put("parentId", params.get("parentId").asText());
        }

        Map<String, Object> folder = noteClient.createFolder(request);
        if (folder.containsKey("error")) return "创建文件夹失败：" + folder.get("error");
        return String.format("已创建文件夹「%s」（ID: %s）。", folder.get("name"), folder.get("id"));
    }

    private String doList() {
        List<Map<String, Object>> folders = noteClient.listFolders();
        if (folders.isEmpty()) return "目前没有任何文件夹";

        StringBuilder sb = new StringBuilder();
        sb.append("共有 ").append(folders.size()).append(" 个文件夹：\n");
        for (int i = 0; i < folders.size(); i++) {
            Map<String, Object> folder = folders.get(i);
            sb.append(String.format("%d. 「%s」(ID: %s)\n", i + 1, folder.get("name"), folder.get("id")));
        }
        return sb.toString();
    }

    private String doRename(JsonNode params) {
        String folderId = params.path("folderId").asText("");
        String newName = params.path("name").asText("");
        if (folderId.isEmpty()) return "缺少 folderId";
        if (newName.isEmpty()) return "缺少 name";

        Map<String, Object> request = new HashMap<>();
        request.put("name", newName);
        Map<String, Object> folder = noteClient.updateFolder(folderId, request);
        if (folder.containsKey("error")) return "重命名失败：文件夹不存在";
        return String.format("已将文件夹重命名为「%s」。", folder.get("name"));
    }

    private String doDelete(JsonNode params) {
        String folderId = params.path("folderId").asText("");
        if (folderId.isEmpty()) return "缺少 folderId";
        return String.format("PENDING_ACTION:{\"type\":\"DELETE_FOLDER\",\"folderId\":\"%s\"}\n确认要删除该文件夹吗？（文件夹内的笔记会移到根目录）", folderId);
    }

    private String doConfirmDelete(JsonNode params) {
        String folderId = params.path("folderId").asText("");
        if (folderId.isEmpty()) return "缺少 folderId";
        noteClient.deleteFolder(folderId);
        return "文件夹已删除，其中的笔记已移到根目录";
    }
}
