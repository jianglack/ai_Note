package com.ainote.app.agent.tools;

import com.ainote.app.agent.pipeline.ToolExecutionPipeline;
import com.ainote.app.model.Folder;
import com.ainote.app.repository.FolderRepository;
import com.ainote.app.security.SecurityUtils;
import com.ainote.app.service.FolderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文件夹复合工具
 * 将 6 个细粒度文件夹工具压缩为 1 个复合工具
 */
@Component
public class FolderActionTool {

    private static final Logger log = LoggerFactory.getLogger(FolderActionTool.class);
    private final FolderService folderService;
    private final FolderRepository folderRepository;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;
    private final ToolExecutionPipeline pipeline;

    public FolderActionTool(FolderService folderService, FolderRepository folderRepository,
                            SecurityUtils securityUtils, ToolExecutionPipeline pipeline) {
        this.folderService = folderService;
        this.folderRepository = folderRepository;
        this.securityUtils = securityUtils;
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
            String userId = securityUtils.getCurrentUserId();

            return pipeline.execute(userId, "folderAction", action, params,
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
            log.warn("Failed to parse paramsJson='{}', using empty object. Error: {}", paramsJson, e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private String doCreate(JsonNode params) {
        String name = params.path("name").asText("");
        if (name.isEmpty()) return "缺少 name";
        String parentId = params.has("parentId") && !params.get("parentId").isNull()
                ? params.get("parentId").asText() : null;

        // 幂等保护
        String userId = securityUtils.getCurrentUserId();
        List<com.ainote.app.entity.Folder> existing = folderRepository.findByUserIdAndName(userId, name);
        if (!existing.isEmpty()) {
            com.ainote.app.entity.Folder f = existing.get(0);
            return String.format("同名文件夹已存在：「%s」（ID: %s），无需重复创建。", f.getName(), f.getId());
        }

        Folder folder = folderService.create(name, parentId);
        String location = parentId == null ? "根目录" : "指定文件夹下";
        return String.format("已在%s创建文件夹「%s」（ID: %s）。", location, folder.getName(), folder.getId());
    }

    private String doList() {
        List<Folder> folders = folderService.listAll();
        if (folders.isEmpty()) return "目前没有任何文件夹";

        StringBuilder sb = new StringBuilder();
        sb.append("共有 ").append(folders.size()).append(" 个文件夹：\n");
        for (int i = 0; i < folders.size(); i++) {
            Folder folder = folders.get(i);
            sb.append(String.format("%d. 「%s」(ID: %s)\n", i + 1, folder.getName(), folder.getId()));
        }
        return sb.toString();
    }

    private String doRename(JsonNode params) {
        String folderId = params.path("folderId").asText("");
        String newName = params.path("name").asText("");
        if (folderId.isEmpty()) return "缺少 folderId";
        if (newName.isEmpty()) return "缺少 name";

        Folder folder = folderService.update(folderId, newName, null);
        if (folder != null) {
            return String.format("已将文件夹重命名为「%s」。", folder.getName());
        }
        return "重命名失败：文件夹不存在";
    }

    private String doDelete(JsonNode params) {
        String folderId = params.path("folderId").asText("");
        if (folderId.isEmpty()) return "缺少 folderId";
        return String.format("PENDING_ACTION:{\"type\":\"DELETE_FOLDER\",\"folderId\":\"%s\"}\n⚠️ 确认要删除该文件夹吗？（文件夹内的笔记会移到根目录）", folderId);
    }

    private String doConfirmDelete(JsonNode params) {
        String folderId = params.path("folderId").asText("");
        if (folderId.isEmpty()) return "缺少 folderId";

        folderService.delete(folderId);
        return "✅ 文件夹已删除，其中的笔记已移到根目录";
    }
}
