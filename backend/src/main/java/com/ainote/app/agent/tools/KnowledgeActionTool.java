package com.ainote.app.agent.tools;

import com.ainote.app.agent.pipeline.ToolExecutionPipeline;
import com.ainote.app.entity.Note;
import com.ainote.app.entity.NoteConcept;
import com.ainote.app.repository.NoteConceptRepository;
import com.ainote.app.repository.NoteRepository;
import com.ainote.app.service.KnowledgeGraphService;
import com.ainote.app.security.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class KnowledgeActionTool {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeActionTool.class);
    private final KnowledgeGraphService graphService;
    private final NoteConceptRepository conceptRepository;
    private final NoteRepository noteRepository;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;
    private final ToolExecutionPipeline pipeline;

    public KnowledgeActionTool(
            KnowledgeGraphService graphService,
            NoteConceptRepository conceptRepository,
            NoteRepository noteRepository,
            SecurityUtils securityUtils,
            ToolExecutionPipeline pipeline,
            ObjectMapper objectMapper) {
        this.graphService = graphService;
        this.conceptRepository = conceptRepository;
        this.noteRepository = noteRepository;
        this.securityUtils = securityUtils;
        this.pipeline = pipeline;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "knowledgeAction", value = """
        知识图谱查询工具。支持的 action:
        - findRelated: 查找与指定笔记相关的笔记。params: {"noteId":"..."}
        - conceptSearch: 按概念/主题搜索笔记。params: {"concepts":["概念1","概念2"]}
        - conceptCloud: 获取用户的概念词云（最常出现的主题）。params: {}
        - noteConcepts: 查看某笔记的所有概念标签。params: {"noteId":"..."}
        - topicOverview: 获取用户笔记的主题概览和统计。params: {}
        """)
    public String knowledgeAction(String action, String paramsJson) {
        String userId = securityUtils.getCurrentUserId();
        log.info("KnowledgeAction: action={}, params={}", action, paramsJson);

        try {
            JsonNode params = (paramsJson != null && !paramsJson.isBlank())
                    ? objectMapper.readTree(paramsJson) : objectMapper.createObjectNode();

            return pipeline.execute(userId, "knowledgeAction", action, params,
                    () -> doExecute(action, userId, params));
        } catch (Exception e) {
            log.error("KnowledgeAction failed: {}", e.getMessage(), e);
            return "{\"error\":\"知识查询失败: " + e.getMessage() + "\"}";
        }
    }

    private String doExecute(String action, String userId, JsonNode params) {
        try {
            return switch (action) {
                case "findRelated" -> findRelatedNotes(userId, params);
                case "conceptSearch" -> conceptSearch(userId, params);
                case "conceptCloud" -> conceptCloud(userId);
                case "noteConcepts" -> noteConcepts(params);
                case "topicOverview" -> topicOverview(userId);
                default -> "{\"error\":\"未知的知识操作: " + action + "\"}";
            };
        } catch (Exception e) {
            log.error("KnowledgeAction doExecute failed: {}", e.getMessage(), e);
            return "{\"error\":\"知识查询失败: " + e.getMessage() + "\"}";
        }
    }

    private String findRelatedNotes(String userId, JsonNode params) throws Exception {
        String noteId = params.path("noteId").asText("");
        if (noteId.isBlank()) return "{\"error\":\"请提供 noteId\"}";

        List<Note> related = graphService.searchRelatedNotes(userId, "", List.of(noteId), 6);
        related = related.stream().filter(n -> !n.getId().equals(noteId)).limit(5).toList();

        if (related.isEmpty()) return "{\"message\":\"没有找到相关笔记\"}";

        List<Map<String, String>> result = related.stream()
                .map(n -> Map.of("id", n.getId(), "title", n.getTitle()))
                .toList();
        return objectMapper.writeValueAsString(result);
    }

    private String conceptSearch(String userId, JsonNode params) throws Exception {
        List<String> concepts = new ArrayList<>();
        if (params.has("concepts") && params.get("concepts").isArray()) {
            params.get("concepts").forEach(c -> concepts.add(c.asText()));
        }
        if (concepts.isEmpty()) return "{\"error\":\"请提供 concepts 数组\"}";

        List<String> noteIds = graphService.findNotesBySharedConcepts(userId, concepts, 10);
        if (noteIds.isEmpty()) {
            // Fallback to PostgreSQL
            List<NoteConcept> dbResults = conceptRepository.findByUserIdAndConceptIn(userId, concepts);
            noteIds = dbResults.stream().map(NoteConcept::getNoteId).distinct().collect(Collectors.toList());
        }
        if (noteIds.isEmpty()) return "{\"message\":\"没有找到包含这些概念的笔记\"}";

        List<Note> notes = noteRepository.findAllById(noteIds);
        List<Map<String, String>> result = notes.stream()
                .map(n -> Map.of("id", n.getId(), "title", n.getTitle()))
                .toList();
        return objectMapper.writeValueAsString(result);
    }

    private String conceptCloud(String userId) throws Exception {
        List<Map<String, Object>> cloud = graphService.getUserConceptCloud(userId, 20);
        if (cloud.isEmpty()) {
            // Fallback to PostgreSQL
            List<NoteConcept> all = conceptRepository.findByUserId(userId);
            Map<String, Long> freq = all.stream()
                    .collect(Collectors.groupingBy(NoteConcept::getConcept, Collectors.counting()));
            cloud = freq.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(20)
                    .map(e -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("concept", e.getKey());
                        m.put("noteCount", e.getValue());
                        return m;
                    })
                    .toList();
        }
        if (cloud.isEmpty()) return "{\"message\":\"暂无概念数据，请先创建一些笔记\"}";
        return objectMapper.writeValueAsString(cloud);
    }

    private String noteConcepts(JsonNode params) throws Exception {
        String noteId = params.path("noteId").asText("");
        if (noteId.isBlank()) return "{\"error\":\"请提供 noteId\"}";

        List<NoteConcept> concepts = conceptRepository.findByNoteId(noteId);
        if (concepts.isEmpty()) return "{\"message\":\"该笔记暂无提取的概念\"}";

        List<Map<String, Object>> result = concepts.stream()
                .map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("concept", c.getConcept());
                    m.put("category", c.getCategory());
                    m.put("confidence", c.getConfidence());
                    return m;
                })
                .toList();
        return objectMapper.writeValueAsString(result);
    }

    private String topicOverview(String userId) throws Exception {
        List<Map<String, Object>> cloud = graphService.getUserConceptCloud(userId, 30);
        if (cloud.isEmpty()) {
            // Fallback
            List<NoteConcept> all = conceptRepository.findByUserId(userId);
            Map<String, List<NoteConcept>> byCategory = all.stream()
                    .collect(Collectors.groupingBy(NoteConcept::getCategory));
            Map<String, Object> result = new HashMap<>();
            result.put("totalConcepts", all.stream().map(NoteConcept::getConcept).distinct().count());
            for (var entry : byCategory.entrySet()) {
                result.put(entry.getKey(), entry.getValue().stream()
                        .map(NoteConcept::getConcept).distinct().limit(10).toList());
            }
            return objectMapper.writeValueAsString(result);
        }

        Map<String, List<String>> byCategory = cloud.stream()
                .collect(Collectors.groupingBy(
                        c -> (String) c.get("category"),
                        Collectors.mapping(c -> (String) c.get("concept"), Collectors.toList())));

        Map<String, Object> result = new HashMap<>();
        result.put("totalConcepts", cloud.size());
        result.putAll(byCategory);
        return objectMapper.writeValueAsString(result);
    }
}
